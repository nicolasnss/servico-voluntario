package com.nicolas.servicovoluntario.regras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class CalculoCotaTest {

    private static final BigDecimal VALOR_COTA = new BigDecimal("760.00");

    @Test
    void cotaInteiraTemOitoHorasEValeOValorDaCota() {
        int horas = CalculoCota.horasContadas(Duration.ofHours(8));

        assertThat(horas).isEqualTo(8);
        assertThat(CalculoCota.cotas(horas)).isEqualByComparingTo("1");
        assertThat(CalculoCota.valor(horas, VALOR_COTA)).isEqualByComparingTo("760.00");
    }

    @Test
    void fracaoDeTrintaMinutosOuMaisContaComoUmaHora() {
        assertThat(CalculoCota.horasContadas(Duration.ofMinutes(4 * 60 + 29))).isEqualTo(4);
        assertThat(CalculoCota.horasContadas(Duration.ofMinutes(4 * 60 + 30))).isEqualTo(5);
        assertThat(CalculoCota.horasContadas(Duration.ofMinutes(3 * 60 + 30))).isEqualTo(4);
    }

    @Test
    void jornadaForaDoIntervaloDeQuatroADozeHorasEhRejeitada() {
        assertThatThrownBy(() -> CalculoCota.horasContadas(Duration.ofMinutes(3 * 60 + 29)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoCota.horasContadas(Duration.ofMinutes(12 * 60 + 30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(CalculoCota.horasContadas(Duration.ofMinutes(12 * 60 + 29))).isEqualTo(12);
    }

    @Test
    void valorEhProporcionalAoValorDaHora() {
        assertThat(CalculoCota.cotas(4)).isEqualByComparingTo("0.5");
        assertThat(CalculoCota.valor(4, VALOR_COTA)).isEqualByComparingTo("380.00");
        assertThat(CalculoCota.valor(6, VALOR_COTA)).isEqualByComparingTo("570.00");
        assertThat(CalculoCota.cotas(12)).isEqualByComparingTo("1.5");
        assertThat(CalculoCota.valor(12, VALOR_COTA)).isEqualByComparingTo("1140.00");
    }
}
