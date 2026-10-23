package com.nicolas.servicovoluntario.aplicacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nicolas.servicovoluntario.aplicacao.Dtos.CandidaturaResumo;
import com.nicolas.servicovoluntario.aplicacao.Dtos.CotaMensal;
import com.nicolas.servicovoluntario.aplicacao.Dtos.NovaCandidatura;
import com.nicolas.servicovoluntario.dominio.Calendario;
import com.nicolas.servicovoluntario.dominio.StatusCandidatura;
import com.nicolas.servicovoluntario.dominio.StatusServico;
import com.nicolas.servicovoluntario.persistencia.AuditoriaRepository;
import com.nicolas.servicovoluntario.persistencia.CandidaturaRepository;
import com.nicolas.servicovoluntario.persistencia.CandidaturaRepository.Detalhe;
import com.nicolas.servicovoluntario.persistencia.PolicialRepository;
import com.nicolas.servicovoluntario.persistencia.ReferenciaRepository;
import com.nicolas.servicovoluntario.persistencia.ServicoRepository;
import com.nicolas.servicovoluntario.regras.CalculoCota;
import com.nicolas.servicovoluntario.regras.Violacao;

/** Ações do policial sobre as próprias candidaturas e a consulta da sua cota mensal. */
@Service
@Transactional
public class CandidaturaService {

    private final IdentidadeService identidade;
    private final ServicoRepository servicos;
    private final CandidaturaRepository candidaturas;
    private final PolicialRepository policiais;
    private final ReferenciaRepository referencia;
    private final AuditoriaRepository auditoria;
    private final ElegibilidadeService elegibilidade;
    private final Valores valores;

    public CandidaturaService(IdentidadeService identidade, ServicoRepository servicos,
                              CandidaturaRepository candidaturas, PolicialRepository policiais,
                              ReferenciaRepository referencia, AuditoriaRepository auditoria,
                              ElegibilidadeService elegibilidade, Valores valores) {
        this.identidade = identidade;
        this.servicos = servicos;
        this.candidaturas = candidaturas;
        this.policiais = policiais;
        this.referencia = referencia;
        this.auditoria = auditoria;
        this.elegibilidade = elegibilidade;
        this.valores = valores;
    }

    public CandidaturaResumo candidatar(Jwt jwt, long servicoId, NovaCandidatura comando) {
        var policial = identidade.policial(jwt);
        var servico = servicos.buscarParaAtualizar(servicoId)
                .orElseThrow(() -> ApiException.naoEncontrado("Serviço não encontrado"));
        var vaga = servicos.vagasDe(servicoId).stream()
                .filter(v -> v.id() == comando.vagaId())
                .findFirst()
                .orElseThrow(() -> ApiException.naoEncontrado("Vaga não encontrada neste serviço"));

        if (!comando.declaracaoArt5Aceita()) {
            throw ApiException.requisicaoInvalida(
                    "É necessário aceitar a declaração do art. 5º da Portaria PMDF nº 1.280/2022");
        }
        var agora = Instant.now();
        if (servico.status() != StatusServico.CAPTACAO_ABERTA
                || (servico.captacaoAbreEm() != null && agora.isBefore(servico.captacaoAbreEm()))
                || (servico.captacaoFechaEm() != null && !agora.isBefore(servico.captacaoFechaEm()))) {
            throw ApiException.conflito("A captação deste serviço não está aberta");
        }
        if (candidaturas.servicosComCandidatura(policial.id()).contains(servicoId)) {
            throw ApiException.conflito("Você já se candidatou a este serviço");
        }
        List<Violacao> violacoes = elegibilidade.para(policial).avaliar(servico, vaga);
        if (!violacoes.isEmpty()) {
            throw ApiException.regrasVioladas(resumir(violacoes), violacoes);
        }

        long id = candidaturas.inscrever(vaga.id(), policial.id(), true)
                .orElseThrow(() -> ApiException.conflito("Você já se candidatou a este serviço"));
        auditoria.registrar(jwt.getSubject(), "CANDIDATURA_CRIADA", "candidatura", id, null,
                Map.of("servicoId", servicoId, "vagaId", vaga.id(), "matricula", policial.matricula()));
        return resumo(detalhe(id), valores.calculadora());
    }

    @Transactional(readOnly = true)
    public List<CandidaturaResumo> minhas(Jwt jwt) {
        var policial = identidade.policial(jwt);
        var calculadora = valores.calculadora();
        return candidaturas.doPolicial(policial.id()).stream().map(d -> resumo(d, calculadora)).toList();
    }

    public void cancelar(Jwt jwt, long id) {
        var policial = identidade.policial(jwt);
        var candidatura = candidaturas.buscar(id)
                .filter(c -> c.policialId() == policial.id())
                .orElseThrow(() -> ApiException.naoEncontrado("Candidatura não encontrada"));
        // Serializa com a abertura, o encerramento e a seleção do mesmo serviço.
        var servico = servicos.buscarParaAtualizar(candidatura.servico().id()).orElseThrow();
        var atual = detalhe(id);
        if (atual.status() != StatusCandidatura.CANDIDATA || servico.status() != StatusServico.CAPTACAO_ABERTA) {
            throw ApiException.conflito("A candidatura só pode ser cancelada enquanto a captação está aberta");
        }
        candidaturas.atualizarStatus(id, StatusCandidatura.CANCELADA);
        auditoria.registrar(jwt.getSubject(), "CANDIDATURA_CANCELADA", "candidatura", id,
                Map.of("status", atual.status().name()), Map.of("status", StatusCandidatura.CANCELADA.name()));
    }

    @Transactional(readOnly = true)
    public CotaMensal minhaCota(Jwt jwt, String anoMes) {
        YearMonth mes;
        try {
            mes = YearMonth.parse(anoMes);
        } catch (DateTimeParseException e) {
            throw ApiException.requisicaoInvalida("Informe o mês no formato AAAA-MM");
        }
        var policial = identidade.policial(jwt);
        var inicio = Calendario.inicioDoMes(mes);
        var fim = Calendario.fimDoMes(mes);
        var planejadas = policiais.cotasPlanejadasNoMes(policial.id(), inicio, fim);
        var realizadas = policiais.cotasRealizadasNoMes(policial.id(), inicio, fim);
        LocalDate ultimoDia = mes.atEndOfMonth();
        var teto = referencia.parametro(ElegibilidadeService.PARAM_TETO_MENSAL, ultimoDia).map(BigDecimal::new).orElse(null);
        var valorCota = referencia.valorCotaVigente(ultimoDia).map(ReferenciaRepository.ValorCota::valor)
                .orElse(BigDecimal.ZERO);
        return new CotaMensal(mes.toString(), planejadas, realizadas, teto,
                realizadas.multiply(valorCota).setScale(2, RoundingMode.HALF_UP));
    }

    private Detalhe detalhe(long id) {
        return candidaturas.buscar(id).orElseThrow();
    }

    private static String resumir(List<Violacao> violacoes) {
        return violacoes.stream().map(Violacao::mensagem).collect(Collectors.joining("; "));
    }

    static CandidaturaResumo resumo(Detalhe d, Valores.Calculadora calculadora) {
        var servico = d.servico();
        return new CandidaturaResumo(
                d.id(),
                servico.id(),
                servico.titulo(),
                servico.local(),
                servico.inicio(),
                servico.fim(),
                servico.horasContadas(),
                CalculoCota.cotas(servico.horasContadas()),
                calculadora.valor(servico),
                d.funcao(),
                d.status(),
                d.posicaoRanking(),
                d.justificativa(),
                d.situacaoExecucao());
    }
}
