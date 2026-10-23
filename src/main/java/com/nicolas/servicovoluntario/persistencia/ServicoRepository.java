package com.nicolas.servicovoluntario.persistencia;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.nicolas.servicovoluntario.dominio.Escopo;
import com.nicolas.servicovoluntario.dominio.Servico;
import com.nicolas.servicovoluntario.dominio.StatusServico;
import com.nicolas.servicovoluntario.dominio.TipoFuncao;
import com.nicolas.servicovoluntario.dominio.Vaga;
import com.nicolas.servicovoluntario.regras.Circulo;

@Repository
public class ServicoRepository {

    /** Colunas lidas por {@link #MAPPER}; outras consultas que juntam o serviço reaproveitam este trecho. */
    static final String COLUNAS = """
            s.id, s.ordem_servico_numero, s.titulo, s.local, s.unidade_id, u.nome AS unidade, s.inicio, s.fim,
            s.horas_contadas, s.captacao_abre_em, s.captacao_fecha_em, s.status
            """;

    /** Função da vaga; quando não informada, cai no nome padrão do tipo. */
    static final String FUNCAO = """
            COALESCE(v.funcao, CASE v.tipo_funcao WHEN 'SUPERVISAO' THEN 'Supervisão' ELSE 'Policial de execução' END)
            """;

    private static final String SELECT = "SELECT " + COLUNAS
            + " FROM servico s JOIN unidade_militar u ON u.id = s.unidade_id ";

    static final RowMapper<Servico> MAPPER = (rs, n) -> new Servico(
            rs.getLong("id"),
            rs.getString("ordem_servico_numero"),
            rs.getString("titulo"),
            rs.getString("local"),
            rs.getLong("unidade_id"),
            rs.getString("unidade"),
            Colunas.instante(rs, "inicio"),
            Colunas.instante(rs, "fim"),
            rs.getInt("horas_contadas"),
            Colunas.instante(rs, "captacao_abre_em"),
            Colunas.instante(rs, "captacao_fecha_em"),
            StatusServico.valueOf(rs.getString("status")));

    private static final RowMapper<Vaga> VAGA = (rs, n) -> new Vaga(
            rs.getLong("id"),
            rs.getLong("servico_id"),
            rs.getString("funcao"),
            TipoFuncao.valueOf(rs.getString("tipo_funcao")),
            rs.getInt("quantidade"),
            circulos(rs));

    public record Totais(int candidatos, int selecionados) {
    }

    private final JdbcClient jdbc;

    public ServicoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Servico> listarPorStatus(Collection<StatusServico> status) {
        return jdbc.sql(SELECT + "WHERE s.status IN (:status) ORDER BY s.inicio, s.id")
                .param("status", status.stream().map(Enum::name).toList())
                .query(MAPPER).list();
    }

    public List<Servico> listarDasUnidades(Escopo escopo) {
        if (escopo.todas()) {
            return jdbc.sql(SELECT + "ORDER BY s.inicio, s.id").query(MAPPER).list();
        }
        if (escopo.unidades().isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + "WHERE s.unidade_id IN (:unidades) ORDER BY s.inicio, s.id")
                .param("unidades", escopo.unidades())
                .query(MAPPER).list();
    }

    public Optional<Servico> buscar(long id) {
        return jdbc.sql(SELECT + "WHERE s.id = :id").param("id", id).query(MAPPER).optional();
    }

    /** Trava a linha até o fim da transação, para serializar mudanças de estado e candidaturas do serviço. */
    public Optional<Servico> buscarParaAtualizar(long id) {
        return jdbc.sql(SELECT + "WHERE s.id = :id FOR UPDATE OF s").param("id", id).query(MAPPER).optional();
    }

    public void atualizarStatus(long id, StatusServico status) {
        jdbc.sql("UPDATE servico SET status = :status WHERE id = :id")
                .param("status", status.name()).param("id", id).update();
    }

    public void abrirCaptacao(long id, Instant agora, String abertaPor) {
        jdbc.sql("""
                UPDATE servico SET status = 'CAPTACAO_ABERTA', captacao_abre_em = :agora, captacao_aberta_por = :por
                 WHERE id = :id
                """)
                .param("agora", Colunas.paraBanco(agora)).param("por", abertaPor).param("id", id).update();
    }

    public Map<Long, List<Vaga>> vagasDe(Collection<Long> servicoIds) {
        if (servicoIds.isEmpty()) {
            return Map.of();
        }
        var vagas = jdbc.sql("SELECT v.id, v.servico_id, v.tipo_funcao, v.quantidade, v.circulos_permitidos, "
                        + FUNCAO + " AS funcao FROM vaga v WHERE v.servico_id IN (:ids) ORDER BY v.id")
                .param("ids", servicoIds)
                .query(VAGA).list();
        Map<Long, List<Vaga>> porServico = new HashMap<>();
        vagas.forEach(v -> porServico.computeIfAbsent(v.servicoId(), k -> new ArrayList<>()).add(v));
        return porServico;
    }

    public List<Vaga> vagasDe(long servicoId) {
        return vagasDe(List.of(servicoId)).getOrDefault(servicoId, List.of());
    }

    public Map<Long, Totais> totais(Collection<Long> servicoIds) {
        if (servicoIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Totais> resultado = new HashMap<>();
        jdbc.sql("""
                SELECT v.servico_id,
                       COUNT(*) AS candidatos,
                       COUNT(*) FILTER (WHERE c.status = 'SELECIONADA') AS selecionados
                  FROM candidatura c JOIN vaga v ON v.id = c.vaga_id
                 WHERE v.servico_id IN (:ids) AND c.status <> 'CANCELADA'
                 GROUP BY v.servico_id
                """)
                .param("ids", servicoIds)
                .query((rs, n) -> resultado.put(rs.getLong("servico_id"),
                        new Totais(rs.getInt("candidatos"), rs.getInt("selecionados"))))
                .list();
        return resultado;
    }

    private static Set<Circulo> circulos(ResultSet rs) throws SQLException {
        Array array = rs.getArray("circulos_permitidos");
        if (array == null) {
            return Set.of();
        }
        var valores = (Object[]) array.getArray();
        return Arrays.stream(valores).map(Object::toString).map(Circulo::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Circulo.class)));
    }
}
