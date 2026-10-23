package com.nicolas.servicovoluntario.persistencia;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.nicolas.servicovoluntario.dominio.Servico;
import com.nicolas.servicovoluntario.dominio.SituacaoExecucao;
import com.nicolas.servicovoluntario.dominio.StatusCandidatura;

@Repository
public class CandidaturaRepository {

    /** Candidatura com o serviço e a função da vaga, como o policial a enxerga. */
    public record Detalhe(
            long id,
            long vagaId,
            long policialId,
            StatusCandidatura status,
            Integer posicaoRanking,
            String justificativa,
            String funcao,
            SituacaoExecucao situacaoExecucao,
            Servico servico) {
    }

    /** Candidatura com os dados do policial, como o responsável pela captação a enxerga. */
    public record Candidato(
            long candidaturaId,
            long vagaId,
            long policialId,
            String matricula,
            String nome,
            String postoGraduacao,
            LocalDate dataAdmissao,
            StatusCandidatura status,
            Integer posicao,
            BigDecimal cotasMesAnterior,
            BigDecimal cotasMesAtual,
            SituacaoExecucao situacaoExecucao) {
    }

    private static final String SELECT_DETALHE = "SELECT c.id AS candidatura_id, c.vaga_id, c.policial_id, "
            + "c.status AS candidatura_status, c.posicao_ranking, c.justificativa, "
            + ServicoRepository.FUNCAO + " AS funcao, e.situacao AS situacao_execucao, "
            + ServicoRepository.COLUNAS
            + """
             FROM candidatura c
                  JOIN vaga v ON v.id = c.vaga_id
                  JOIN servico s ON s.id = v.servico_id
                  JOIN unidade_militar u ON u.id = s.unidade_id
                  LEFT JOIN execucao e ON e.candidatura_id = c.id
            """;

    private static final RowMapper<Detalhe> DETALHE = (rs, n) -> new Detalhe(
            rs.getLong("candidatura_id"),
            rs.getLong("vaga_id"),
            rs.getLong("policial_id"),
            StatusCandidatura.valueOf(rs.getString("candidatura_status")),
            Colunas.inteiroOuNulo(rs, "posicao_ranking"),
            rs.getString("justificativa"),
            rs.getString("funcao"),
            situacao(rs.getString("situacao_execucao")),
            ServicoRepository.MAPPER.mapRow(rs, n));

    private static final RowMapper<Candidato> CANDIDATO = (rs, n) -> new Candidato(
            rs.getLong("candidatura_id"),
            rs.getLong("vaga_id"),
            rs.getLong("policial_id"),
            rs.getString("matricula"),
            rs.getString("nome"),
            rs.getString("posto_graduacao"),
            rs.getObject("data_admissao", LocalDate.class),
            StatusCandidatura.valueOf(rs.getString("status")),
            Colunas.inteiroOuNulo(rs, "posicao_ranking"),
            rs.getBigDecimal("cotas_mes_anterior"),
            rs.getBigDecimal("cotas_mes_atual"),
            situacao(rs.getString("situacao_execucao")));

    private final JdbcClient jdbc;

    public CandidaturaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Detalhe> buscar(long id) {
        return jdbc.sql(SELECT_DETALHE + "WHERE c.id = :id").param("id", id).query(DETALHE).optional();
    }

    public List<Detalhe> doPolicial(long policialId) {
        return jdbc.sql(SELECT_DETALHE + "WHERE c.policial_id = :p ORDER BY s.inicio DESC, c.id DESC")
                .param("p", policialId).query(DETALHE).list();
    }

    /** Serviços em que o policial tem candidatura que não foi cancelada. */
    public Set<Long> servicosComCandidatura(long policialId) {
        return new HashSet<>(jdbc.sql("""
                SELECT DISTINCT v.servico_id
                  FROM candidatura c JOIN vaga v ON v.id = c.vaga_id
                 WHERE c.policial_id = :p AND c.status <> 'CANCELADA'
                """)
                .param("p", policialId).query(Long.class).list());
    }

    /**
     * Cria a candidatura ou reativa uma cancelada. Vazio quando já existe candidatura não cancelada
     * para o mesmo policial e a mesma vaga.
     */
    public Optional<Long> inscrever(long vagaId, long policialId, boolean declaracaoArt5Aceita) {
        return jdbc.sql("""
                INSERT INTO candidatura (vaga_id, policial_id, declaracao_art5_aceita)
                VALUES (:vaga, :policial, :declaracao)
                ON CONFLICT (vaga_id, policial_id) DO UPDATE
                   SET status = 'CANDIDATA', inscrita_em = now(), declaracao_art5_aceita = EXCLUDED.declaracao_art5_aceita,
                       cotas_mes_anterior = NULL, cotas_mes_atual = NULL, posicao_ranking = NULL,
                       divergiu_do_ranking = FALSE, decidida_por = NULL, decidida_em = NULL, justificativa = NULL
                 WHERE candidatura.status = 'CANCELADA'
                RETURNING id
                """)
                .param("vaga", vagaId).param("policial", policialId).param("declaracao", declaracaoArt5Aceita)
                .query(Long.class).optional();
    }

    public void atualizarStatus(long id, StatusCandidatura status) {
        jdbc.sql("UPDATE candidatura SET status = :status WHERE id = :id")
                .param("status", status.name()).param("id", id).update();
    }

    public List<Candidato> candidatosDoServico(long servicoId) {
        return jdbc.sql("""
                SELECT c.id AS candidatura_id, c.vaga_id, c.policial_id, c.status, c.posicao_ranking,
                       c.cotas_mes_anterior, c.cotas_mes_atual,
                       p.matricula, p.nome, p.posto_graduacao, p.data_admissao, e.situacao AS situacao_execucao
                  FROM candidatura c
                       JOIN vaga v ON v.id = c.vaga_id
                       JOIN policial p ON p.id = c.policial_id
                       LEFT JOIN execucao e ON e.candidatura_id = c.id
                 WHERE v.servico_id = :servico AND c.status <> 'CANCELADA'
                 ORDER BY c.vaga_id, c.posicao_ranking, c.id
                """)
                .param("servico", servicoId).query(CANDIDATO).list();
    }

    public void gravarPosicao(long id, BigDecimal cotasMesAnterior, BigDecimal cotasMesAtual, int posicao) {
        jdbc.sql("""
                UPDATE candidatura SET cotas_mes_anterior = :anterior, cotas_mes_atual = :atual, posicao_ranking = :posicao
                 WHERE id = :id
                """)
                .param("anterior", cotasMesAnterior).param("atual", cotasMesAtual)
                .param("posicao", posicao).param("id", id).update();
    }

    public void decidir(long id, StatusCandidatura status, boolean divergiuDoRanking, String decididaPor,
                        Instant decididaEm, String justificativa) {
        jdbc.sql("""
                UPDATE candidatura SET status = :status, divergiu_do_ranking = :divergiu, decidida_por = :por,
                       decidida_em = :em, justificativa = :justificativa
                 WHERE id = :id
                """)
                .param("status", status.name()).param("divergiu", divergiuDoRanking).param("por", decididaPor)
                .param("em", Colunas.paraBanco(decididaEm)).param("justificativa", justificativa)
                .param("id", id).update();
    }

    private static SituacaoExecucao situacao(String valor) {
        return valor == null ? null : SituacaoExecucao.valueOf(valor);
    }
}
