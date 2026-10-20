package com.nicolas.servicovoluntario.regras;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AvaliadorElegibilidadeTest {

    private static final List<RegraDescanso> DESCANSOS = List.of(new RegraDescanso(12, 6), new RegraDescanso(24, 12));
    private final AvaliadorElegibilidade avaliador = AvaliadorElegibilidade.padrao(DESCANSOS);

    private static Instant t(String hora) {
        return Instant.parse("2026-11-10T" + hora + ":00Z");
    }

    private static Periodo p(String inicio, String fim) {
        return new Periodo(t(inicio), t(fim));
    }

    /** Serviço de 8h, das 14h às 22h, sem nenhum impedimento. */
    private static Ctx livre() {
        return new Ctx();
    }

    private static List<String> codigos(List<Violacao> violacoes) {
        return violacoes.stream().map(Violacao::codigo).toList();
    }

    @Test
    void policialLivreEstaElegivel() {
        assertThat(avaliador.avaliar(livre().build())).isEmpty();
    }

    @Test
    void servicoSobrepostoAEscalaRegularEhRejeitado() {
        var ctx = livre().escala(p("06:00", "18:00")).build();

        assertThat(codigos(avaliador.avaliar(ctx))).containsExactly("CHOQUE_ESCALA_REGULAR");
    }

    @Test
    void escalaDe24hExigeDescansoDe12h() {
        var escala24h = new Periodo(t("06:00").minusSeconds(24 * 3600), t("06:00"));

        var antes = livre().servico(p("17:59", "23:59")).escala(escala24h).build();
        var depois = livre().servico(p("18:00", "23:59")).escala(escala24h).build();

        assertThat(codigos(avaliador.avaliar(antes))).containsExactly("DESCANSO_INSUFICIENTE");
        assertThat(avaliador.avaliar(depois)).isEmpty();
    }

    @Test
    void escalaDe12hExigeDescansoDe6h() {
        var escala12h = new Periodo(t("07:00").minusSeconds(12 * 3600), t("07:00"));

        var antes = livre().servico(p("12:59", "20:59")).escala(escala12h).build();
        var depois = livre().servico(p("13:00", "21:00")).escala(escala12h).build();

        assertThat(codigos(avaliador.avaliar(antes))).containsExactly("DESCANSO_INSUFICIENTE");
        assertThat(avaliador.avaliar(depois)).isEmpty();
    }

    @Test
    void escalaCurtaNaoGeraDescanso() {
        var ctx = livre().escala(p("08:00", "13:59")).build();

        assertThat(avaliador.avaliar(ctx)).isEmpty();
    }

    @Test
    void servicoSobrepostoAOutroJaSelecionadoEhRejeitado() {
        var ctx = livre().outroServico(p("20:00", "23:00")).build();

        assertThat(codigos(avaliador.avaliar(ctx))).containsExactly("CHOQUE_OUTRO_SERVICO");
    }

    @Test
    void servicosEmSequenciaSemSobreposicaoSaoPermitidos() {
        var ctx = livre().outroServico(p("06:00", "14:00")).build();

        assertThat(avaliador.avaliar(ctx)).isEmpty();
    }

    @Test
    void afastamentoEBloqueioImpedemACandidatura() {
        var ctx = livre().afastamento(p("00:00", "23:59")).bloqueio(p("00:00", "23:59")).build();

        assertThat(codigos(avaliador.avaliar(ctx))).containsExactly("AFASTADO", "BLOQUEADO");
    }

    @Test
    void funcaoEspecialNaoAcumulaComSvg() {
        var ctx = livre().impedidoPorFuncaoEspecial().build();

        assertThat(codigos(avaliador.avaliar(ctx))).containsExactly("IMPEDIMENTO_FUNCAO_ESPECIAL");
    }

    @Test
    void vagaRestritaACirculoRejeitaOutroCirculo() {
        var oficial = livre().circuloPolicial(Circulo.OFICIAL).circulosPermitidos(Circulo.PRACA).build();
        var praca = livre().circuloPolicial(Circulo.PRACA).circulosPermitidos(Circulo.PRACA).build();

        assertThat(codigos(avaliador.avaliar(oficial))).containsExactly("CIRCULO_NAO_PERMITIDO");
        assertThat(avaliador.avaliar(praca)).isEmpty();
    }

    @Test
    void tetoIndividualConsideraCotasJaPlanejadasMaisAEstaCandidatura() {
        var noLimite = livre().cotasPlanejadas("5.000").teto("6.000").build();
        var acima = livre().cotasPlanejadas("5.001").teto("6.000").build();
        var semTeto = livre().cotasPlanejadas("40").build();

        assertThat(avaliador.avaliar(noLimite)).isEmpty();
        assertThat(codigos(avaliador.avaliar(acima))).containsExactly("TETO_INDIVIDUAL_EXCEDIDO");
        assertThat(avaliador.avaliar(semTeto)).isEmpty();
    }

    @Test
    void acumulaTodasAsViolacoes() {
        var ctx = livre().escala(p("10:00", "16:00")).impedidoPorFuncaoEspecial()
                .circuloPolicial(Circulo.OFICIAL).circulosPermitidos(Circulo.PRACA).build();

        assertThat(codigos(avaliador.avaliar(ctx)))
                .containsExactly("CHOQUE_ESCALA_REGULAR", "IMPEDIMENTO_FUNCAO_ESPECIAL", "CIRCULO_NAO_PERMITIDO");
    }

    private static final class Ctx {
        private Periodo servico = p("14:00", "22:00");
        private Circulo circuloPolicial = Circulo.PRACA;
        private Set<Circulo> circulosPermitidos = Set.of();
        private final List<Periodo> escala = new ArrayList<>();
        private final List<Periodo> afastamentos = new ArrayList<>();
        private final List<Periodo> bloqueios = new ArrayList<>();
        private final List<Periodo> outros = new ArrayList<>();
        private boolean funcaoEspecial;
        private BigDecimal planejadas = BigDecimal.ZERO;
        private BigDecimal teto;

        Ctx servico(Periodo p) { servico = p; return this; }
        Ctx escala(Periodo p) { escala.add(p); return this; }
        Ctx afastamento(Periodo p) { afastamentos.add(p); return this; }
        Ctx bloqueio(Periodo p) { bloqueios.add(p); return this; }
        Ctx outroServico(Periodo p) { outros.add(p); return this; }
        Ctx impedidoPorFuncaoEspecial() { funcaoEspecial = true; return this; }
        Ctx circuloPolicial(Circulo c) { circuloPolicial = c; return this; }
        Ctx circulosPermitidos(Circulo c) { circulosPermitidos = Set.of(c); return this; }
        Ctx cotasPlanejadas(String v) { planejadas = new BigDecimal(v); return this; }
        Ctx teto(String v) { teto = new BigDecimal(v); return this; }

        CandidaturaContexto build() {
            return new CandidaturaContexto(servico, 8, circuloPolicial, circulosPermitidos,
                    escala, afastamentos, bloqueios, outros, funcaoEspecial, planejadas, teto);
        }
    }
}
