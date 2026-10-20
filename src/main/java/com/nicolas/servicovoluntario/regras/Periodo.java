package com.nicolas.servicovoluntario.regras;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record Periodo(Instant inicio, Instant fim) {

    public Periodo {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fim, "fim");
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("fim deve ser posterior ao inicio");
        }
    }

    public boolean sobrepoe(Periodo outro) {
        return inicio.isBefore(outro.fim) && outro.inicio.isBefore(fim);
    }

    public Duration duracao() {
        return Duration.between(inicio, fim);
    }
}
