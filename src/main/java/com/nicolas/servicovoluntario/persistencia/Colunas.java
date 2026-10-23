package com.nicolas.servicovoluntario.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class Colunas {

    private Colunas() {
    }

    /** O driver do PostgreSQL não aceita {@link Instant} como parâmetro; {@link OffsetDateTime} sim. */
    static OffsetDateTime paraBanco(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }

    static Instant instante(ResultSet rs, String coluna) throws SQLException {
        var valor = rs.getObject(coluna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    static Integer inteiroOuNulo(ResultSet rs, String coluna) throws SQLException {
        int valor = rs.getInt(coluna);
        return rs.wasNull() ? null : valor;
    }
}
