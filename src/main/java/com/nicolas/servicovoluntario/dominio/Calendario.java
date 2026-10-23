package com.nicolas.servicovoluntario.dominio;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

/** Competências e limites de mês seguem o horário de Brasília, mesmo com os instantes gravados em UTC. */
public final class Calendario {

    public static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private Calendario() {
    }

    public static YearMonth mesDe(Instant instante) {
        return YearMonth.from(instante.atZone(ZONA));
    }

    public static Instant inicioDoMes(YearMonth mes) {
        return mes.atDay(1).atStartOfDay(ZONA).toInstant();
    }

    /** Primeiro instante do mês seguinte, para usar como limite exclusivo. */
    public static Instant fimDoMes(YearMonth mes) {
        return inicioDoMes(mes.plusMonths(1));
    }
}
