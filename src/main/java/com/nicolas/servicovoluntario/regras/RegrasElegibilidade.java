package com.nicolas.servicovoluntario.regras;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class RegrasElegibilidade {

    private RegrasElegibilidade() {
    }

    /** O SVG só pode ser prestado na folga (Decreto 39.627, art. 2º), respeitado o descanso após a escala. */
    public static RegraElegibilidade escalaRegular(List<RegraDescanso> descansos) {
        return ctx -> {
            for (Periodo escala : ctx.escalaRegular()) {
                if (escala.sobrepoe(ctx.servico())) {
                    return Optional.of(new Violacao("CHOQUE_ESCALA_REGULAR",
                            "O serviço coincide com a escala ordinária do policial"));
                }
            }
            for (Periodo escala : ctx.escalaRegular()) {
                var fimDescanso = escala.fim().plus(descansoApos(escala, descansos));
                if (!ctx.servico().inicio().isBefore(escala.fim()) && ctx.servico().inicio().isBefore(fimDescanso)) {
                    return Optional.of(new Violacao("DESCANSO_INSUFICIENTE",
                            "O serviço começa antes do fim do descanso mínimo após a escala ordinária"));
                }
            }
            return Optional.empty();
        };
    }

    public static RegraElegibilidade outroServico() {
        return ctx -> ctx.outrosServicosSelecionados().stream()
                .filter(ctx.servico()::sobrepoe)
                .findFirst()
                .map(p -> new Violacao("CHOQUE_OUTRO_SERVICO",
                        "O policial já foi selecionado para outro serviço no mesmo horário"));
    }

    public static RegraElegibilidade afastamento() {
        return ctx -> ctx.afastamentos().stream()
                .filter(ctx.servico()::sobrepoe)
                .findFirst()
                .map(p -> new Violacao("AFASTADO", "O policial está afastado no período do serviço"));
    }

    public static RegraElegibilidade bloqueio() {
        return ctx -> ctx.bloqueios().stream()
                .filter(ctx.servico()::sobrepoe)
                .findFirst()
                .map(p -> new Violacao("BLOQUEADO", "O policial está impedido de se inscrever no período"));
    }

    /** Lei 10.486/2002, art. 3º, VII: a gratificação de função de natureza especial não acumula com o SVG. */
    public static RegraElegibilidade funcaoEspecial() {
        return ctx -> ctx.impedidoPorFuncaoEspecial()
                ? Optional.of(new Violacao("IMPEDIMENTO_FUNCAO_ESPECIAL",
                        "Gratificação de função de natureza especial não é acumulável com o SVG"))
                : Optional.empty();
    }

    public static RegraElegibilidade circuloPermitido() {
        return ctx -> ctx.circulosPermitidos().isEmpty() || ctx.circulosPermitidos().contains(ctx.circuloPolicial())
                ? Optional.empty()
                : Optional.of(new Violacao("CIRCULO_NAO_PERMITIDO",
                        "A vaga não é destinada ao círculo hierárquico do policial"));
    }

    public static RegraElegibilidade tetoIndividualMensal() {
        return ctx -> {
            if (ctx.tetoIndividualMensal() == null) {
                return Optional.empty();
            }
            var total = ctx.cotasPlanejadasNoMes().add(CalculoCota.cotas(ctx.horasContadas()));
            return total.compareTo(ctx.tetoIndividualMensal()) > 0
                    ? Optional.of(new Violacao("TETO_INDIVIDUAL_EXCEDIDO",
                            "A candidatura excede o teto individual mensal de cotas"))
                    : Optional.empty();
        };
    }

    private static Duration descansoApos(Periodo escala, List<RegraDescanso> descansos) {
        long horas = escala.duracao().toHours();
        return descansos.stream()
                .filter(r -> horas >= r.escalaMinimaHoras())
                .max(Comparator.comparingInt(RegraDescanso::escalaMinimaHoras))
                .map(r -> Duration.ofHours(r.descansoHoras()))
                .orElse(Duration.ZERO);
    }
}
