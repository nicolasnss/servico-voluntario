package com.nicolas.servicovoluntario.regras;

import java.util.List;
import java.util.Optional;

public final class AvaliadorElegibilidade {

    private final List<RegraElegibilidade> regras;

    public AvaliadorElegibilidade(List<RegraElegibilidade> regras) {
        this.regras = List.copyOf(regras);
    }

    public static AvaliadorElegibilidade padrao(List<RegraDescanso> descansos) {
        return new AvaliadorElegibilidade(List.of(
                RegrasElegibilidade.escalaRegular(descansos),
                RegrasElegibilidade.outroServico(),
                RegrasElegibilidade.afastamento(),
                RegrasElegibilidade.bloqueio(),
                RegrasElegibilidade.funcaoEspecial(),
                RegrasElegibilidade.circuloPermitido(),
                RegrasElegibilidade.tetoIndividualMensal()));
    }

    /** Devolve todas as violações, para que o policial veja de uma vez tudo o que o impede de se candidatar. */
    public List<Violacao> avaliar(CandidaturaContexto contexto) {
        return regras.stream()
                .map(regra -> regra.avaliar(contexto))
                .flatMap(Optional::stream)
                .toList();
    }
}
