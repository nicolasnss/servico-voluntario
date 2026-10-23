package com.nicolas.servicovoluntario.aplicacao;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.nicolas.servicovoluntario.dominio.Calendario;
import com.nicolas.servicovoluntario.dominio.Policial;
import com.nicolas.servicovoluntario.dominio.Servico;
import com.nicolas.servicovoluntario.dominio.Vaga;
import com.nicolas.servicovoluntario.persistencia.PolicialRepository;
import com.nicolas.servicovoluntario.persistencia.PolicialRepository.ServicoSelecionado;
import com.nicolas.servicovoluntario.persistencia.ReferenciaRepository;
import com.nicolas.servicovoluntario.regras.AvaliadorElegibilidade;
import com.nicolas.servicovoluntario.regras.CandidaturaContexto;
import com.nicolas.servicovoluntario.regras.Periodo;
import com.nicolas.servicovoluntario.regras.RegraDescanso;
import com.nicolas.servicovoluntario.regras.Violacao;

/** Monta o contexto de um policial e aplica as regras de elegibilidade do pacote {@code regras}. */
@Service
public class ElegibilidadeService {

    /** Sem valor vigente em {@code parametro}, não há teto individual. */
    static final String PARAM_TETO_MENSAL = "TETO_INDIVIDUAL_COTAS_MES";
    static final String PARAM_DESCANSO_12H = "DESCANSO_HORAS_APOS_ESCALA_12H";
    static final String PARAM_DESCANSO_24H = "DESCANSO_HORAS_APOS_ESCALA_24H";

    /** Folga para enxergar a escala que termina pouco antes do serviço e exige descanso. */
    private static final Duration JANELA_ANTERIOR = Duration.ofDays(3);

    private final PolicialRepository policiais;
    private final ReferenciaRepository referencia;

    public ElegibilidadeService(PolicialRepository policiais, ReferenciaRepository referencia) {
        this.policiais = policiais;
        this.referencia = referencia;
    }

    /** Carrega o histórico do policial uma vez, para avaliar vários serviços sem repetir as consultas. */
    public Avaliacao para(Policial policial) {
        var desde = Instant.now().minus(JANELA_ANTERIOR);
        var desdeData = desde.atZone(Calendario.ZONA).toLocalDate();
        return new Avaliacao(
                policial,
                policiais.escalasDesde(policial.id(), desde),
                policiais.afastamentosDesde(policial.id(), desdeData),
                policiais.bloqueiosDesde(policial.id(), desdeData),
                policiais.impedimentosDesde(policial.id(), desdeData),
                policiais.servicosSelecionadosDesde(policial.id(), desde));
    }

    private record Parametros(List<RegraDescanso> descansos, BigDecimal tetoMensal) {
    }

    public final class Avaliacao {

        private final Policial policial;
        private final List<Periodo> escalas;
        private final List<Periodo> afastamentos;
        private final List<Periodo> bloqueios;
        private final List<Periodo> impedimentos;
        private final List<ServicoSelecionado> selecionados;
        private final Map<LocalDate, Parametros> parametros = new HashMap<>();

        private Avaliacao(Policial policial, List<Periodo> escalas, List<Periodo> afastamentos,
                          List<Periodo> bloqueios, List<Periodo> impedimentos, List<ServicoSelecionado> selecionados) {
            this.policial = policial;
            this.escalas = escalas;
            this.afastamentos = afastamentos;
            this.bloqueios = bloqueios;
            this.impedimentos = impedimentos;
            this.selecionados = selecionados;
        }

        public List<Violacao> avaliar(Servico servico, Vaga vaga) {
            var periodo = servico.periodo();
            var vigentes = parametros.computeIfAbsent(
                    servico.inicio().atZone(Calendario.ZONA).toLocalDate(), ElegibilidadeService.this::parametros);
            var planejadas = BigDecimal.ZERO;
            if (vigentes.tetoMensal() != null) {
                var mes = Calendario.mesDe(servico.inicio());
                planejadas = policiais.cotasPlanejadasNoMes(
                        policial.id(), Calendario.inicioDoMes(mes), Calendario.fimDoMes(mes), servico.id());
            }
            var contexto = new CandidaturaContexto(
                    periodo,
                    servico.horasContadas(),
                    policial.circulo(),
                    vaga.circulosPermitidos(),
                    escalas,
                    afastamentos,
                    bloqueios,
                    selecionados.stream().filter(s -> s.servicoId() != servico.id()).map(ServicoSelecionado::periodo).toList(),
                    impedimentos.stream().anyMatch(periodo::sobrepoe),
                    planejadas,
                    vigentes.tetoMensal());
            return AvaliadorElegibilidade.padrao(vigentes.descansos()).avaliar(contexto);
        }

        /**
         * Resultado para o serviço como um todo: elegível se alguma vaga aceita o policial; senão, as violações
         * de todas as vagas, sem repetir o mesmo código.
         */
        public List<Violacao> avaliar(Servico servico, List<Vaga> vagas) {
            Map<String, Violacao> todas = new LinkedHashMap<>();
            for (Vaga vaga : vagas) {
                var violacoes = avaliar(servico, vaga);
                if (violacoes.isEmpty()) {
                    return List.of();
                }
                violacoes.forEach(v -> todas.putIfAbsent(v.codigo(), v));
            }
            return new ArrayList<>(todas.values());
        }
    }

    private Parametros parametros(LocalDate data) {
        List<RegraDescanso> descansos = new ArrayList<>();
        referencia.parametro(PARAM_DESCANSO_12H, data)
                .ifPresent(horas -> descansos.add(new RegraDescanso(12, Integer.parseInt(horas))));
        referencia.parametro(PARAM_DESCANSO_24H, data)
                .ifPresent(horas -> descansos.add(new RegraDescanso(24, Integer.parseInt(horas))));
        var teto = referencia.parametro(PARAM_TETO_MENSAL, data).map(BigDecimal::new).orElse(null);
        return new Parametros(descansos, teto);
    }
}
