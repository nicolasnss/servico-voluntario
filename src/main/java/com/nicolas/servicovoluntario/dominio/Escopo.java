package com.nicolas.servicovoluntario.dominio;

import java.util.Set;

/** Unidades em que um perfil pode atuar; {@code todas} corresponde a unidade nula em {@code papel_unidade}. */
public record Escopo(boolean todas, Set<Long> unidades) {

    public Escopo {
        unidades = Set.copyOf(unidades);
    }

    public boolean permite(long unidadeId) {
        return todas || unidades.contains(unidadeId);
    }
}
