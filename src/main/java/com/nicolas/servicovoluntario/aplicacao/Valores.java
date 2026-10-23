package com.nicolas.servicovoluntario.aplicacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.nicolas.servicovoluntario.dominio.Calendario;
import com.nicolas.servicovoluntario.dominio.Servico;
import com.nicolas.servicovoluntario.persistencia.ReferenciaRepository;
import com.nicolas.servicovoluntario.regras.CalculoCota;

/** Valor em reais de um serviço, pelo valor da cota vigente na data em que ele começa. */
@Component
public class Valores {

    private final ReferenciaRepository referencia;

    public Valores(ReferenciaRepository referencia) {
        this.referencia = referencia;
    }

    /** Lembra o valor vigente de cada data, para listas com vários serviços no mesmo dia. */
    public Calculadora calculadora() {
        return new Calculadora();
    }

    public final class Calculadora {

        private final Map<LocalDate, BigDecimal> cotaPorData = new HashMap<>();

        public BigDecimal valor(Servico servico) {
            var data = servico.inicio().atZone(Calendario.ZONA).toLocalDate();
            var valorCota = cotaPorData.computeIfAbsent(data, d -> referencia.valorCotaVigente(d)
                    .map(ReferenciaRepository.ValorCota::valor)
                    .orElse(BigDecimal.ZERO));
            return CalculoCota.valor(servico.horasContadas(), valorCota);
        }
    }
}
