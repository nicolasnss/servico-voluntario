package com.nicolas.servicovoluntario.persistencia;

import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.json.JsonMapper;

/** A tabela é append-only (trigger no banco); aqui só se insere. */
@Repository
public class AuditoriaRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AuditoriaRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void registrar(String usuarioSub, String acao, String entidade, long entidadeId,
                          Map<String, ?> anterior, Map<String, ?> novo) {
        jdbc.sql("""
                INSERT INTO auditoria (usuario_sub, acao, entidade, entidade_id, valor_anterior, valor_novo)
                VALUES (:sub, :acao, :entidade, :id, CAST(:anterior AS jsonb), CAST(:novo AS jsonb))
                """)
                .param("sub", usuarioSub).param("acao", acao).param("entidade", entidade).param("id", entidadeId)
                .param("anterior", anterior == null ? null : json.writeValueAsString(anterior), java.sql.Types.VARCHAR)
                .param("novo", novo == null ? null : json.writeValueAsString(novo), java.sql.Types.VARCHAR)
                .update();
    }
}
