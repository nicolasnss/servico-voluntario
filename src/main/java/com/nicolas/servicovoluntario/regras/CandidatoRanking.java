package com.nicolas.servicovoluntario.regras;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CandidatoRanking(
        String matricula,
        BigDecimal cotasMesAnterior,
        BigDecimal cotasMesAtual,
        LocalDate dataAdmissao) {
}
