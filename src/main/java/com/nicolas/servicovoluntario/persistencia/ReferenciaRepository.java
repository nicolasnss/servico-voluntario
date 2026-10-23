package com.nicolas.servicovoluntario.persistencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Dados de referência com vigência: valor da cota e parâmetros da Portaria. */
@Repository
public class ReferenciaRepository {

    public record ValorCota(long id, BigDecimal valor) {
    }

    private final JdbcClient jdbc;

    public ReferenciaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ValorCota> valorCotaVigente(LocalDate data) {
        return jdbc.sql("""
                SELECT id, valor FROM valor_cota WHERE vigente_desde <= :data
                 ORDER BY vigente_desde DESC LIMIT 1
                """)
                .param("data", data)
                .query((rs, n) -> new ValorCota(rs.getLong("id"), rs.getBigDecimal("valor")))
                .optional();
    }

    public Optional<String> parametro(String chave, LocalDate data) {
        return jdbc.sql("""
                SELECT valor FROM parametro WHERE chave = :chave AND vigente_desde <= :data
                 ORDER BY vigente_desde DESC LIMIT 1
                """)
                .param("chave", chave).param("data", data)
                .query(String.class).optional();
    }
}
