package com.nicolas.servicovoluntario.dominio;

import java.time.Instant;

import com.nicolas.servicovoluntario.regras.Periodo;

public record Servico(
        long id,
        String ordemServico,
        String titulo,
        String local,
        long unidadeId,
        String unidade,
        Instant inicio,
        Instant fim,
        int horasContadas,
        Instant captacaoAbreEm,
        Instant captacaoFechaEm,
        StatusServico status) {

    public Periodo periodo() {
        return new Periodo(inicio, fim);
    }
}
