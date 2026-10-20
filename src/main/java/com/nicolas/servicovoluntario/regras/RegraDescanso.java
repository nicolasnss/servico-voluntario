package com.nicolas.servicovoluntario.regras;

/** Descanso exigido após uma escala ordinária com duração de pelo menos {@code escalaMinimaHoras}. */
public record RegraDescanso(int escalaMinimaHoras, int descansoHoras) {
}
