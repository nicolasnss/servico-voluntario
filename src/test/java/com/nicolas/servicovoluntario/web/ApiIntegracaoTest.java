package com.nicolas.servicovoluntario.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.nicolas.servicovoluntario.dominio.Calendario;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Percorre a API inteira contra um PostgreSQL real (embutido, sem Docker), com as migrations Flyway aplicadas.
 * A autenticação é simulada com JWTs; o Keycloak não participa.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegracaoTest {

    private static final EmbeddedPostgres POSTGRES = iniciarPostgres();

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registro.add("spring.datasource.username", () -> "postgres");
        registro.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void encerrar() throws IOException {
        POSTGRES.close();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private long servicoId;
    private long vagaId;

    @BeforeEach
    void prepararDados() {
        jdbc.sql("""
                TRUNCATE auditoria, execucao, bloqueio, candidatura, vaga, servico, escala_regular, afastamento,
                         impedimento_policial, historico_svg_importado, papel_unidade, policial, unidade_militar
                RESTART IDENTITY CASCADE
                """).update();
        jdbc.sql("INSERT INTO unidade_militar (id, nome, sigla, tipo) VALUES (1, '1º Batalhão', '1º BPM', 'BATALHAO')").update();
        jdbc.sql("INSERT INTO unidade_militar (id, nome, sigla, tipo) VALUES (2, '2º Batalhão', '2º BPM', 'BATALHAO')").update();
        policial(1, "1000001", "Sd. Alves", "Soldado", "2018-03-01", 1);
        policial(2, "1000002", "Cb. Barros", "Cabo", "2012-05-10", 1);
        policial(3, "1000003", "Sd. Cunha", "Soldado", "2020-01-15", 1);
        policial(9, "1000099", "Cap. Captação", "Capitão", "2005-01-01", 1);
        policial(8, "1000098", "Cap. Outra Unidade", "Capitão", "2005-01-01", 2);

        servicoId = jdbc.sql("""
                INSERT INTO servico (titulo, local, unidade_id, inicio, fim, horas_contadas, status)
                VALUES ('Policiamento de evento', 'Parque da Cidade', 1,
                        date_trunc('hour', now()) + interval '5 days', date_trunc('hour', now()) + interval '5 days 8 hours',
                        8, 'RASCUNHO')
                RETURNING id
                """).query(Long.class).single();
        vagaId = jdbc.sql("INSERT INTO vaga (servico_id, quantidade) VALUES (:s, 2) RETURNING id")
                .param("s", servicoId).query(Long.class).single();
    }

    @Test
    void fluxoCompletoDaCaptacaoAteOPagamento() throws Exception {
        // Mês anterior: Alves tem 3 cotas, Cunha 1, Barros nenhuma. Ranking esperado: Barros, Cunha, Alves.
        var anterior = Calendario.mesDe(java.time.Instant.now()).minusMonths(1).toString();
        historico(1, anterior, 3);
        historico(3, anterior, 1);

        mvc.perform(post("/api/v1/captacao/servicos/{id}/abrir", servicoId).with(captacao()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/captacao/servicos/{id}/abrir", servicoId).with(captacao()))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/servicos").with(policial("1000001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", is("CAPTACAO_ABERTA")))
                .andExpect(jsonPath("$[0].elegibilidade.elegivel", is(true)))
                .andExpect(jsonPath("$[0].jaCandidatou", is(false)))
                .andExpect(jsonPath("$[0].cotas", is(1.0)))
                .andExpect(jsonPath("$[0].valor", is(760.0)))
                .andExpect(jsonPath("$[0].vagas[0].funcao", is("Policial de execução")));

        for (var matricula : new String[] {"1000001", "1000002", "1000003"}) {
            mvc.perform(post("/api/v1/servicos/{id}/candidaturas", servicoId).with(policial(matricula))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"vagaId\":%d,\"declaracaoArt5Aceita\":true}".formatted(vagaId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status", is("CANDIDATA")));
        }
        mvc.perform(post("/api/v1/servicos/{id}/candidaturas", servicoId).with(policial("1000001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vagaId\":%d,\"declaracaoArt5Aceita\":true}".formatted(vagaId)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/servicos/{id}", servicoId).with(policial("1000001")))
                .andExpect(jsonPath("$.jaCandidatou", is(true)));

        mvc.perform(get("/api/v1/captacao/servicos/{id}", servicoId).with(captacao()))
                .andExpect(jsonPath("$.totalCandidatos", is(3)));

        mvc.perform(post("/api/v1/captacao/servicos/{id}/encerrar", servicoId).with(captacao()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/captacao/servicos/{id}/ranking", servicoId).with(captacao()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matricula", is("1000002")))
                .andExpect(jsonPath("$[0].posicao", is(1)))
                .andExpect(jsonPath("$[1].matricula", is("1000003")))
                .andExpect(jsonPath("$[2].matricula", is("1000001")))
                .andExpect(jsonPath("$[2].cotasMesAnterior", is(3.0)));

        // Escolher Alves e Barros foge do ranking (Barros e Cunha): exige justificativa.
        var divergente = "{\"candidaturaIds\":[%d,%d],\"justificativa\":%s}";
        mvc.perform(post("/api/v1/captacao/servicos/{id}/selecionar", servicoId).with(captacao())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(divergente.formatted(candidatura("1000001"), candidatura("1000002"), "null")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", is("Seleção diferente do ranking exige justificativa")));
        mvc.perform(post("/api/v1/captacao/servicos/{id}/selecionar", servicoId).with(captacao())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidaturaIds\":[%d,%d,%d]}".formatted(
                                candidatura("1000001"), candidatura("1000002"), candidatura("1000003"))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/captacao/servicos/{id}/selecionar", servicoId).with(captacao())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(divergente.formatted(candidatura("1000001"), candidatura("1000002"),
                                "\"Cunha estará de licença nesse período\"")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/minhas-candidaturas").with(policial("1000003")))
                .andExpect(jsonPath("$[0].status", is("NAO_SELECIONADA")))
                .andExpect(jsonPath("$[0].posicaoRanking", is(2)))
                .andExpect(jsonPath("$[0].justificativa", is("Cunha estará de licença nesse período")));
        mvc.perform(get("/api/v1/minhas-candidaturas").with(policial("1000001")))
                .andExpect(jsonPath("$[0].status", is("SELECIONADA")))
                .andExpect(jsonPath("$[0].valor", is(760.0)));

        // O serviço ainda está no futuro: não dá para lançar presença.
        mvc.perform(post("/api/v1/execucoes/{id}", candidatura("1000001")).with(captacao())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"situacao\":\"PRESENTE\"}"))
                .andExpect(status().isConflict());

        jdbc.sql("""
                UPDATE servico SET inicio = date_trunc('hour', now()) - interval '10 hours',
                                   fim = date_trunc('hour', now()) - interval '2 hours' WHERE id = :id
                """).param("id", servicoId).update();

        mvc.perform(post("/api/v1/execucoes/{id}", candidatura("1000001")).with(captacao())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"situacao\":\"PRESENTE\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/execucoes/{id}", candidatura("1000001")).with(captacao())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"situacao\":\"FALTOU\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/captacao/servicos/{id}", servicoId).with(captacao()))
                .andExpect(jsonPath("$.status", is("SELECIONADO")));
        mvc.perform(post("/api/v1/execucoes/{id}", candidatura("1000002")).with(captacao())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"situacao\":\"FALTOU\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/captacao/servicos/{id}", servicoId).with(captacao()))
                .andExpect(jsonPath("$.status", is("REALIZADO")));

        var mes = Calendario.mesDe(java.time.Instant.now().minus(java.time.Duration.ofHours(10))).toString();
        mvc.perform(get("/api/v1/minha-cota").param("anoMes", mes).with(policial("1000001")))
                .andExpect(jsonPath("$.planejadas", is(1.0)))
                .andExpect(jsonPath("$.realizadas", is(1.0)))
                .andExpect(jsonPath("$.valorPrevisto", is(760.0)));
        mvc.perform(get("/api/v1/minha-cota").param("anoMes", mes).with(policial("1000002")))
                .andExpect(jsonPath("$.planejadas", is(1.0)))
                .andExpect(jsonPath("$.realizadas", is(0.0)));

        // Abertura, 3 candidaturas, encerramento, seleção e 2 lançamentos.
        var acoes = jdbc.sql("SELECT acao FROM auditoria ORDER BY id").query(String.class).list();
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(
                "CAPTACAO_ABERTA", "CANDIDATURA_CRIADA", "CANDIDATURA_CRIADA", "CANDIDATURA_CRIADA",
                "CAPTACAO_ENCERRADA", "SELECAO_REALIZADA", "EXECUCAO_LANCADA", "EXECUCAO_LANCADA"), acoes);
    }

    @Test
    void candidaturaFeridaPelasRegrasDeElegibilidadeEhRecusadaComAsViolacoes() throws Exception {
        abrirCaptacao();
        // Escala ordinária de Barros coincide com o serviço.
        jdbc.sql("""
                INSERT INTO escala_regular (policial_id, inicio, fim)
                SELECT 2, inicio, fim FROM servico WHERE id = :id
                """).param("id", servicoId).update();

        mvc.perform(get("/api/v1/servicos/{id}", servicoId).with(policial("1000002")))
                .andExpect(jsonPath("$.elegibilidade.elegivel", is(false)))
                .andExpect(jsonPath("$.elegibilidade.violacoes[0].codigo", is("CHOQUE_ESCALA_REGULAR")));
        mvc.perform(post("/api/v1/servicos/{id}/candidaturas", servicoId).with(policial("1000002"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vagaId\":%d,\"declaracaoArt5Aceita\":true}".formatted(vagaId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violacoes[0].codigo", is("CHOQUE_ESCALA_REGULAR")));
    }

    @Test
    void candidaturaExigeADeclaracaoDoArtigoQuinto() throws Exception {
        abrirCaptacao();
        mvc.perform(post("/api/v1/servicos/{id}/candidaturas", servicoId).with(policial("1000001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vagaId\":%d,\"declaracaoArt5Aceita\":false}".formatted(vagaId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelarLiberaANovaCandidatura() throws Exception {
        abrirCaptacao();
        var corpo = "{\"vagaId\":%d,\"declaracaoArt5Aceita\":true}".formatted(vagaId);
        mvc.perform(post("/api/v1/servicos/{id}/candidaturas", servicoId).with(policial("1000001"))
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isCreated());

        mvc.perform(delete("/api/v1/candidaturas/{id}", candidatura("1000001")).with(policial("1000002")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/candidaturas/{id}", candidatura("1000001")).with(policial("1000001")))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/servicos/{id}/candidaturas", servicoId).with(policial("1000001"))
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/minhas-candidaturas").with(policial("1000001")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", is("CANDIDATA")));
    }

    @Test
    void captacaoSoAtendeAUnidadeDoResponsavelEAoPerfilCerto() throws Exception {
        mvc.perform(get("/api/v1/captacao/servicos").with(policial("1000001")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/captacao/servicos/{id}/abrir", servicoId).with(captacaoDe("1000098")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/captacao/servicos").with(captacaoDe("1000098")))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/captacao/servicos").with(captacao()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", is("RASCUNHO")));
    }

    @Test
    void semTokenOuSemCadastroOAcessoEhNegado() throws Exception {
        mvc.perform(get("/api/v1/servicos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/servicos").with(policial("9999999")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", is("Matrícula 9999999 não cadastrada no SVG")));
    }

    @Test
    void rascunhoNaoApareceParaOPolicial() throws Exception {
        mvc.perform(get("/api/v1/servicos").with(policial("1000001")))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/servicos/{id}", servicoId).with(policial("1000001")))
                .andExpect(status().isNotFound());
    }

    private void abrirCaptacao() throws Exception {
        mvc.perform(post("/api/v1/captacao/servicos/{id}/abrir", servicoId).with(captacao()))
                .andExpect(status().isNoContent());
    }

    private void policial(long id, String matricula, String nome, String posto, String admissao, long unidade) {
        jdbc.sql("""
                INSERT INTO policial (id, matricula, nome, posto_graduacao, circulo, data_admissao, unidade_id)
                VALUES (:id, :matricula, :nome, :posto, 'PRACA', CAST(:admissao AS date), :unidade)
                """)
                .param("id", id).param("matricula", matricula).param("nome", nome).param("posto", posto)
                .param("admissao", admissao).param("unidade", unidade).update();
    }

    private void historico(long policialId, String anoMes, double cotas) {
        jdbc.sql("INSERT INTO historico_svg_importado (policial_id, ano_mes, cotas, origem) VALUES (:p, :m, :c, 'TESTE')")
                .param("p", policialId).param("m", anoMes).param("c", cotas).update();
    }

    private long candidatura(String matricula) {
        return jdbc.sql("""
                SELECT c.id FROM candidatura c JOIN policial p ON p.id = c.policial_id WHERE p.matricula = :m
                """).param("m", matricula).query(Long.class).single();
    }

    private static RequestPostProcessor policial(String matricula) {
        return usuario(matricula, "POLICIAL");
    }

    private static RequestPostProcessor captacao() {
        return captacaoDe("1000099");
    }

    private static RequestPostProcessor captacaoDe(String matricula) {
        return usuario(matricula, "POLICIAL", "RESP_CAPTACAO");
    }

    private static RequestPostProcessor usuario(String matricula, String... perfis) {
        return jwt()
                .jwt(j -> j.subject("sub-" + matricula).claim("matricula", matricula))
                .authorities(Arrays.stream(perfis).map(p -> new SimpleGrantedAuthority("ROLE_" + p))
                        .toArray(SimpleGrantedAuthority[]::new));
    }

    private static EmbeddedPostgres iniciarPostgres() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível iniciar o PostgreSQL embutido", e);
        }
    }
}
