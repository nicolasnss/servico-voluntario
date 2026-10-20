package com.nicolas.servicovoluntario.regras;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * Cálculo de cotas conforme o Decreto DF 39.627/2019, art. 2º, § 2º e art. 3º, parágrafo único:
 * a cota equivale a 8 horas e pode ser fracionada entre 4 e 12 horas, com valor proporcional;
 * fração de hora igual ou superior a 30 minutos conta como uma hora.
 */
public final class CalculoCota {

    public static final int HORAS_POR_COTA = 8;
    public static final int HORAS_MINIMAS = 4;
    public static final int HORAS_MAXIMAS = 12;

    private static final BigDecimal BD_HORAS_POR_COTA = BigDecimal.valueOf(HORAS_POR_COTA);

    private CalculoCota() {
    }

    public static int horasContadas(Duration duracao) {
        long minutos = duracao.toMinutes();
        long horas = minutos / 60 + (minutos % 60 >= 30 ? 1 : 0);
        if (horas < HORAS_MINIMAS || horas > HORAS_MAXIMAS) {
            throw new IllegalArgumentException(
                    "Jornada de %d horas fora do intervalo permitido (%d a %d)"
                            .formatted(horas, HORAS_MINIMAS, HORAS_MAXIMAS));
        }
        return (int) horas;
    }

    public static BigDecimal cotas(int horasContadas) {
        return BigDecimal.valueOf(horasContadas).divide(BD_HORAS_POR_COTA, 3, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal valor(int horasContadas, BigDecimal valorCota) {
        return valorCota.multiply(BigDecimal.valueOf(horasContadas))
                .divide(BD_HORAS_POR_COTA, 2, RoundingMode.HALF_UP);
    }
}
