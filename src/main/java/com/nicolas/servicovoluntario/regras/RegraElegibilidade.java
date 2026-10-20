package com.nicolas.servicovoluntario.regras;

import java.util.Optional;

@FunctionalInterface
public interface RegraElegibilidade {

    Optional<Violacao> avaliar(CandidaturaContexto contexto);
}
