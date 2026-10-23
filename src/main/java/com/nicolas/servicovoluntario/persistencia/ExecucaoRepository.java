package com.nicolas.servicovoluntario.persistencia;

import java.math.BigDecimal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.nicolas.servicovoluntario.dominio.SituacaoExecucao;

@Repository
public class ExecucaoRepository {

    private final JdbcClient jdbc;

    public ExecucaoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return {@code false} quando a candidatura já tinha execução lançada */
    public boolean inserir(long candidaturaId, SituacaoExecucao situacao, int horasContadas, BigDecimal cotas,
                           long valorCotaId, String competenciaPagamento, String lancadoPor) {
        return jdbc.sql("""
                INSERT INTO execucao (candidatura_id, situacao, horas_contadas, cotas, valor_cota_id,
                                      competencia_pagamento, lancado_por)
                VALUES (:candidatura, :situacao, :horas, :cotas, :valorCota, :competencia, :por)
                ON CONFLICT (candidatura_id) DO NOTHING
                """)
                .param("candidatura", candidaturaId).param("situacao", situacao.name()).param("horas", horasContadas)
                .param("cotas", cotas).param("valorCota", valorCotaId).param("competencia", competenciaPagamento)
                .param("por", lancadoPor).update() == 1;
    }

    /** Selecionados do serviço que ainda não tiveram presença ou falta lançada. */
    public int pendentes(long servicoId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM candidatura c JOIN vaga v ON v.id = c.vaga_id
                 WHERE v.servico_id = :servico AND c.status = 'SELECIONADA'
                   AND NOT EXISTS (SELECT 1 FROM execucao e WHERE e.candidatura_id = c.id)
                """)
                .param("servico", servicoId).query(Integer.class).single();
    }
}
