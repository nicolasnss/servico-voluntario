package com.nicolas.servicovoluntario.persistencia;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.nicolas.servicovoluntario.dominio.Calendario;
import com.nicolas.servicovoluntario.dominio.Escopo;
import com.nicolas.servicovoluntario.dominio.Policial;
import com.nicolas.servicovoluntario.regras.Circulo;
import com.nicolas.servicovoluntario.regras.Periodo;

/** Policial, seu escopo de unidade e o histórico que alimenta as regras de elegibilidade e o ranking. */
@Repository
public class PolicialRepository {

    /** Fim em aberto (afastamento ou impedimento sem data final). */
    private static final Instant SEM_FIM = Instant.parse("9999-01-01T00:00:00Z");

    public record ServicoSelecionado(long servicoId, Periodo periodo) {
    }

    private final JdbcClient jdbc;

    public PolicialRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Policial> porMatricula(String matricula) {
        return jdbc.sql("""
                SELECT id, matricula, nome, posto_graduacao, circulo, data_admissao, unidade_id
                  FROM policial WHERE matricula = :matricula
                """)
                .param("matricula", matricula)
                .query((rs, n) -> new Policial(
                        rs.getLong("id"),
                        rs.getString("matricula"),
                        rs.getString("nome"),
                        rs.getString("posto_graduacao"),
                        Circulo.valueOf(rs.getString("circulo")),
                        rs.getObject("data_admissao", LocalDate.class),
                        rs.getLong("unidade_id")))
                .optional();
    }

    /** Unidades em que o usuário tem o perfil; unidade nula em {@code papel_unidade} vale para todas. */
    public Optional<Escopo> escopo(String keycloakSub, String perfil) {
        var linhas = jdbc.sql("SELECT unidade_id FROM papel_unidade WHERE keycloak_sub = :sub AND perfil = :perfil")
                .param("sub", keycloakSub).param("perfil", perfil)
                .query((rs, n) -> {
                    long unidade = rs.getLong("unidade_id");
                    return rs.wasNull() ? null : unidade;
                }).list();
        if (linhas.isEmpty()) {
            return Optional.empty();
        }
        Set<Long> unidades = new HashSet<>();
        boolean todas = false;
        for (Long unidade : linhas) {
            if (unidade == null) {
                todas = true;
            } else {
                unidades.add(unidade);
            }
        }
        return Optional.of(new Escopo(todas, unidades));
    }

    public List<Periodo> escalasDesde(long policialId, Instant desde) {
        return jdbc.sql("SELECT inicio, fim FROM escala_regular WHERE policial_id = :p AND fim >= :desde")
                .param("p", policialId).param("desde", Colunas.paraBanco(desde))
                .query((rs, n) -> new Periodo(Colunas.instante(rs, "inicio"), Colunas.instante(rs, "fim")))
                .list();
    }

    public List<Periodo> afastamentosDesde(long policialId, LocalDate desde) {
        return jdbc.sql("""
                SELECT data_inicio, data_fim FROM afastamento
                 WHERE policial_id = :p AND (data_fim IS NULL OR data_fim >= :desde)
                """)
                .param("p", policialId).param("desde", desde)
                .query((rs, n) -> dias(rs, "data_inicio", "data_fim")).list();
    }

    public List<Periodo> bloqueiosDesde(long policialId, LocalDate desde) {
        return jdbc.sql("SELECT inicio, fim FROM bloqueio WHERE policial_id = :p AND fim >= :desde")
                .param("p", policialId).param("desde", desde)
                .query((rs, n) -> dias(rs, "inicio", "fim")).list();
    }

    public List<Periodo> impedimentosDesde(long policialId, LocalDate desde) {
        return jdbc.sql("""
                SELECT vigente_desde, vigente_ate FROM impedimento_policial
                 WHERE policial_id = :p AND (vigente_ate IS NULL OR vigente_ate >= :desde)
                """)
                .param("p", policialId).param("desde", desde)
                .query((rs, n) -> dias(rs, "vigente_desde", "vigente_ate")).list();
    }

    public List<ServicoSelecionado> servicosSelecionadosDesde(long policialId, Instant desde) {
        return jdbc.sql("""
                SELECT s.id, s.inicio, s.fim
                  FROM candidatura c JOIN vaga v ON v.id = c.vaga_id JOIN servico s ON s.id = v.servico_id
                 WHERE c.policial_id = :p AND c.status = 'SELECIONADA' AND s.status <> 'CANCELADO' AND s.fim >= :desde
                """)
                .param("p", policialId).param("desde", Colunas.paraBanco(desde))
                .query((rs, n) -> new ServicoSelecionado(rs.getLong("id"),
                        new Periodo(Colunas.instante(rs, "inicio"), Colunas.instante(rs, "fim"))))
                .list();
    }

    /** Cotas já selecionadas no mês (planejadas), exceto as do serviço em avaliação. */
    public BigDecimal cotasPlanejadasNoMes(long policialId, Instant inicioMes, Instant fimMes, long excetoServicoId) {
        return jdbc.sql("""
                SELECT ROUND(COALESCE(SUM(s.horas_contadas / 8.0), 0), 3)
                  FROM candidatura c JOIN vaga v ON v.id = c.vaga_id JOIN servico s ON s.id = v.servico_id
                 WHERE c.policial_id = :p AND c.status = 'SELECIONADA' AND s.status <> 'CANCELADO'
                   AND s.inicio >= :ini AND s.inicio < :fim AND s.id <> :excecao
                """)
                .param("p", policialId).param("ini", Colunas.paraBanco(inicioMes))
                .param("fim", Colunas.paraBanco(fimMes)).param("excecao", excetoServicoId)
                .query(BigDecimal.class).single();
    }

    public BigDecimal cotasRealizadasNoMes(long policialId, Instant inicioMes, Instant fimMes) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(e.cotas), 0)
                  FROM execucao e JOIN candidatura c ON c.id = e.candidatura_id
                       JOIN vaga v ON v.id = c.vaga_id JOIN servico s ON s.id = v.servico_id
                 WHERE c.policial_id = :p AND s.inicio >= :ini AND s.inicio < :fim
                """)
                .param("p", policialId).param("ini", Colunas.paraBanco(inicioMes)).param("fim", Colunas.paraBanco(fimMes))
                .query(BigDecimal.class).single();
    }

    public BigDecimal cotasPlanejadasNoMes(long policialId, Instant inicioMes, Instant fimMes) {
        return cotasPlanejadasNoMes(policialId, inicioMes, fimMes, -1);
    }

    /**
     * Cotas do mês usadas no ranking: histórico importado mais o que foi selecionado, valendo o realizado quando
     * já houve lançamento e o planejado quando ainda não.
     */
    public Map<Long, BigDecimal> cotasNoMes(Collection<Long> policialIds, String anoMes, Instant inicioMes,
                                            Instant fimMes) {
        Map<Long, BigDecimal> cotas = new HashMap<>();
        if (policialIds.isEmpty()) {
            return cotas;
        }
        jdbc.sql("""
                SELECT policial_id, SUM(cotas) AS cotas FROM historico_svg_importado
                 WHERE ano_mes = :anoMes AND policial_id IN (:ids) GROUP BY policial_id
                """)
                .param("anoMes", anoMes).param("ids", policialIds)
                .query((rs, n) -> cotas.merge(rs.getLong("policial_id"), rs.getBigDecimal("cotas"), BigDecimal::add))
                .list();
        jdbc.sql("""
                SELECT c.policial_id, ROUND(SUM(COALESCE(e.cotas, s.horas_contadas / 8.0)), 3) AS cotas
                  FROM candidatura c JOIN vaga v ON v.id = c.vaga_id JOIN servico s ON s.id = v.servico_id
                       LEFT JOIN execucao e ON e.candidatura_id = c.id
                 WHERE c.status = 'SELECIONADA' AND s.status <> 'CANCELADO' AND c.policial_id IN (:ids)
                   AND s.inicio >= :ini AND s.inicio < :fim
                 GROUP BY c.policial_id
                """)
                .param("ids", policialIds).param("ini", Colunas.paraBanco(inicioMes)).param("fim", Colunas.paraBanco(fimMes))
                .query((rs, n) -> cotas.merge(rs.getLong("policial_id"), rs.getBigDecimal("cotas"), BigDecimal::add))
                .list();
        return cotas;
    }

    /** Período em dias corridos; a data final é inclusiva e, se ausente, o período não termina. */
    private static Periodo dias(ResultSet rs, String colunaInicio, String colunaFim) throws SQLException {
        var inicio = rs.getObject(colunaInicio, LocalDate.class).atStartOfDay(Calendario.ZONA).toInstant();
        var fim = rs.getObject(colunaFim, LocalDate.class);
        return new Periodo(inicio, fim == null ? SEM_FIM : fim.plusDays(1).atStartOfDay(Calendario.ZONA).toInstant());
    }
}
