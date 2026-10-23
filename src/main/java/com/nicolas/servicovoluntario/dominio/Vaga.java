package com.nicolas.servicovoluntario.dominio;

import java.util.Set;

import com.nicolas.servicovoluntario.regras.Circulo;

/** @param circulosPermitidos vazio significa que a vaga aceita qualquer círculo */
public record Vaga(
        long id,
        long servicoId,
        String funcao,
        TipoFuncao tipoFuncao,
        int quantidade,
        Set<Circulo> circulosPermitidos) {
}
