package com.nicolas.servicovoluntario.aplicacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nicolas.servicovoluntario.aplicacao.Dtos.CandidatoCaptacao;
import com.nicolas.servicovoluntario.aplicacao.Dtos.SelecaoCaptacao;
import com.nicolas.servicovoluntario.aplicacao.Dtos.ServicoCaptacao;
import com.nicolas.servicovoluntario.aplicacao.Dtos.VagaServico;
import com.nicolas.servicovoluntario.dominio.Calendario;
import com.nicolas.servicovoluntario.dominio.Servico;
import com.nicolas.servicovoluntario.dominio.SituacaoExecucao;
import com.nicolas.servicovoluntario.dominio.StatusCandidatura;
import com.nicolas.servicovoluntario.dominio.StatusServico;
import com.nicolas.servicovoluntario.dominio.Vaga;
import com.nicolas.servicovoluntario.persistencia.AuditoriaRepository;
import com.nicolas.servicovoluntario.persistencia.CandidaturaRepository;
import com.nicolas.servicovoluntario.persistencia.CandidaturaRepository.Candidato;
import com.nicolas.servicovoluntario.persistencia.ExecucaoRepository;
import com.nicolas.servicovoluntario.persistencia.PolicialRepository;
import com.nicolas.servicovoluntario.persistencia.ReferenciaRepository;
import com.nicolas.servicovoluntario.persistencia.ServicoRepository;
import com.nicolas.servicovoluntario.persistencia.ServicoRepository.Totais;
import com.nicolas.servicovoluntario.regras.CalculoCota;
import com.nicolas.servicovoluntario.regras.CandidatoRanking;
import com.nicolas.servicovoluntario.regras.Ranking;
import com.nicolas.servicovoluntario.regras.Violacao;

/** Operações do responsável pela captação da unidade: abrir, encerrar, selecionar e lançar presenças. */
@Service
@Transactional
public class CaptacaoService {

    static final int MINIMO_JUSTIFICATIVA = 10;

    private final IdentidadeService identidade;
    private final ServicoRepository servicos;
    private final CandidaturaRepository candidaturas;
    private final PolicialRepository policiais;
    private final ExecucaoRepository execucoes;
    private final ReferenciaRepository referencia;
    private final AuditoriaRepository auditoria;
    private final ElegibilidadeService elegibilidade;

    public CaptacaoService(IdentidadeService identidade, ServicoRepository servicos,
                           CandidaturaRepository candidaturas, PolicialRepository policiais,
                           ExecucaoRepository execucoes, ReferenciaRepository referencia,
                           AuditoriaRepository auditoria, ElegibilidadeService elegibilidade) {
        this.identidade = identidade;
        this.servicos = servicos;
        this.candidaturas = candidaturas;
        this.policiais = policiais;
        this.execucoes = execucoes;
        this.referencia = referencia;
        this.auditoria = auditoria;
        this.elegibilidade = elegibilidade;
    }

    @Transactional(readOnly = true)
    public List<ServicoCaptacao> listar(Jwt jwt) {
        var policial = identidade.policial(jwt);
        var escopo = identidade.escopo(jwt, policial, IdentidadeService.PERFIL_CAPTACAO);
        var lista = servicos.listarDasUnidades(escopo);
        var ids = lista.stream().map(Servico::id).toList();
        var vagas = servicos.vagasDe(ids);
        var totais = servicos.totais(ids);
        return lista.stream()
                .map(s -> resumo(s, vagas.getOrDefault(s.id(), List.of()), totais.get(s.id())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ServicoCaptacao obter(Jwt jwt, long id) {
        var servico = carregar(jwt, id, false);
        return resumo(servico, servicos.vagasDe(id), servicos.totais(List.of(id)).get(id));
    }

    @Transactional(readOnly = true)
    public List<CandidatoCaptacao> ranking(Jwt jwt, long id) {
        var servico = carregar(jwt, id, false);
        return posicionar(servico, candidaturas.candidatosDoServico(id)).stream()
                .map(CaptacaoService::candidato)
                .toList();
    }

    public void abrir(Jwt jwt, long id) {
        var servico = carregar(jwt, id, true);
        if (servico.status() != StatusServico.RASCUNHO) {
            throw ApiException.conflito("Só é possível abrir a captação de um rascunho");
        }
        if (servicos.vagasDe(id).isEmpty()) {
            throw ApiException.conflito("O serviço não tem vagas cadastradas");
        }
        var agora = Instant.now();
        if (servico.captacaoFechaEm() != null && !servico.captacaoFechaEm().isAfter(agora)) {
            throw ApiException.conflito("O prazo de captação deste serviço já terminou");
        }
        var policial = identidade.policial(jwt);
        servicos.abrirCaptacao(id, agora, policial.nome());
        auditoria.registrar(jwt.getSubject(), "CAPTACAO_ABERTA", "servico", id,
                Map.of("status", servico.status().name()), Map.of("status", StatusServico.CAPTACAO_ABERTA.name()));
    }

    /** Congela o ranking: a posição e as cotas de cada candidato ficam gravadas para a seleção e a auditoria. */
    public void encerrar(Jwt jwt, long id) {
        var servico = carregar(jwt, id, true);
        if (servico.status() != StatusServico.CAPTACAO_ABERTA) {
            throw ApiException.conflito("A captação não está aberta");
        }
        for (var posicionado : posicionar(servico, candidaturas.candidatosDoServico(id))) {
            candidaturas.gravarPosicao(posicionado.candidato().candidaturaId(), posicionado.cotasMesAnterior(),
                    posicionado.cotasMesAtual(), posicionado.posicao());
        }
        servicos.atualizarStatus(id, StatusServico.CAPTACAO_ENCERRADA);
        auditoria.registrar(jwt.getSubject(), "CAPTACAO_ENCERRADA", "servico", id,
                Map.of("status", servico.status().name()), Map.of("status", StatusServico.CAPTACAO_ENCERRADA.name()));
    }

    public void selecionar(Jwt jwt, long id, SelecaoCaptacao selecao) {
        var servico = carregar(jwt, id, true);
        if (servico.status() != StatusServico.CAPTACAO_ENCERRADA) {
            throw ApiException.conflito("A seleção só pode ser feita depois de encerrada a captação");
        }
        var candidatos = candidaturas.candidatosDoServico(id);
        if (candidatos.stream().anyMatch(c -> c.status() != StatusCandidatura.CANDIDATA)) {
            throw ApiException.conflito("A seleção deste serviço já foi realizada");
        }

        var escolhidos = new LinkedHashSet<>(selecao.candidaturaIds());
        if (escolhidos.size() != selecao.candidaturaIds().size()) {
            throw ApiException.requisicaoInvalida("A seleção repete candidaturas");
        }
        if (escolhidos.isEmpty()) {
            throw ApiException.requisicaoInvalida("Selecione ao menos um candidato");
        }
        var posicionados = posicionar(servico, candidatos);
        var porCandidatura = posicionados.stream()
                .collect(Collectors.toMap(p -> p.candidato().candidaturaId(), Function.identity()));
        if (!porCandidatura.keySet().containsAll(escolhidos)) {
            throw ApiException.requisicaoInvalida("A seleção contém candidaturas que não pertencem a este serviço");
        }

        var vagas = servicos.vagasDe(id);
        boolean divergiu = false;
        for (Vaga vaga : vagas) {
            var daVaga = posicionados.stream().filter(p -> p.candidato().vagaId() == vaga.id()).toList();
            Set<Long> escolhidosDaVaga = daVaga.stream().map(p -> p.candidato().candidaturaId())
                    .filter(escolhidos::contains).collect(Collectors.toSet());
            if (escolhidosDaVaga.size() > vaga.quantidade()) {
                throw ApiException.regrasVioladas(
                        "A vaga “%s” admite no máximo %d selecionados".formatted(vaga.funcao(), vaga.quantidade()),
                        List.of());
            }
            Set<Long> sugeridos = daVaga.stream().limit(vaga.quantidade())
                    .map(p -> p.candidato().candidaturaId()).collect(Collectors.toSet());
            divergiu |= !escolhidosDaVaga.equals(sugeridos);
        }
        var justificativa = selecao.justificativa() == null ? null : selecao.justificativa().strip();
        if (divergiu && (justificativa == null || justificativa.length() < MINIMO_JUSTIFICATIVA)) {
            throw ApiException.regrasVioladas("Seleção diferente do ranking exige justificativa", List.of());
        }

        exigirElegibilidade(servico, vagas, posicionados, escolhidos);

        var agora = Instant.now();
        for (var posicionado : posicionados) {
            var candidatura = posicionado.candidato().candidaturaId();
            var status = escolhidos.contains(candidatura) ? StatusCandidatura.SELECIONADA : StatusCandidatura.NAO_SELECIONADA;
            candidaturas.decidir(candidatura, status, divergiu, jwt.getSubject(), agora,
                    justificativa == null || justificativa.isEmpty() ? null : justificativa);
        }
        servicos.atualizarStatus(id, StatusServico.SELECIONADO);
        auditoria.registrar(jwt.getSubject(), "SELECAO_REALIZADA", "servico", id,
                Map.of("status", servico.status().name()),
                Map.of("status", StatusServico.SELECIONADO.name(), "candidaturaIds", escolhidos,
                        "divergiuDoRanking", divergiu, "justificativa", justificativa == null ? "" : justificativa));
    }

    /**
     * Presença ou falta de quem foi selecionado. Só vale depois do início do serviço; quando o último selecionado
     * é lançado, o serviço passa a realizado.
     */
    public void lancarExecucao(Jwt jwt, long candidaturaId, SituacaoExecucao situacao) {
        var candidatura = candidaturas.buscar(candidaturaId)
                .orElseThrow(() -> ApiException.naoEncontrado("Candidatura não encontrada"));
        var servico = carregar(jwt, candidatura.servico().id(), true);
        var atual = candidaturas.buscar(candidaturaId).orElseThrow();
        if (atual.status() != StatusCandidatura.SELECIONADA
                || (servico.status() != StatusServico.SELECIONADO && servico.status() != StatusServico.REALIZADO)) {
            throw ApiException.conflito("Só é possível lançar presença ou falta de quem foi selecionado");
        }
        if (servico.inicio().isAfter(Instant.now())) {
            throw ApiException.conflito("O serviço ainda não começou");
        }
        LocalDate dataValor = servico.inicio().atZone(Calendario.ZONA).toLocalDate();
        var valorCota = referencia.valorCotaVigente(dataValor)
                .orElseThrow(() -> ApiException.conflito("Não há valor de cota vigente em " + dataValor));

        int horas = situacao == SituacaoExecucao.PRESENTE ? servico.horasContadas() : 0;
        // PROVISÓRIO: a competência de pagamento é o mês em que o serviço termina, até a regra ser confirmada.
        var competencia = Calendario.mesDe(servico.fim()).toString();
        if (!execucoes.inserir(candidaturaId, situacao, horas, CalculoCota.cotas(horas), valorCota.id(), competencia,
                jwt.getSubject())) {
            throw ApiException.conflito("Presença ou falta já lançada para esta candidatura");
        }
        if (execucoes.pendentes(servico.id()) == 0) {
            servicos.atualizarStatus(servico.id(), StatusServico.REALIZADO);
        }
        auditoria.registrar(jwt.getSubject(), "EXECUCAO_LANCADA", "candidatura", candidaturaId, null,
                Map.of("situacao", situacao.name(), "horasContadas", horas, "competenciaPagamento", competencia));
    }

    private void exigirElegibilidade(Servico servico, List<Vaga> vagas, List<Posicionado> posicionados,
                                     Set<Long> escolhidos) {
        Map<Long, Vaga> vagaPorId = vagas.stream().collect(Collectors.toMap(Vaga::id, Function.identity()));
        List<Violacao> violacoes = new ArrayList<>();
        for (var posicionado : posicionados) {
            var candidato = posicionado.candidato();
            if (!escolhidos.contains(candidato.candidaturaId())) {
                continue;
            }
            var policial = policiais.porMatricula(candidato.matricula()).orElseThrow();
            for (var violacao : elegibilidade.para(policial).avaliar(servico, vagaPorId.get(candidato.vagaId()))) {
                violacoes.add(new Violacao(violacao.codigo(), candidato.nome() + ": " + violacao.mensagem()));
            }
        }
        if (!violacoes.isEmpty()) {
            throw ApiException.regrasVioladas(
                    violacoes.stream().map(Violacao::mensagem).collect(Collectors.joining("; ")), violacoes);
        }
    }

    /**
     * Posição de cada candidato dentro da vaga. Depois do encerramento vale o que foi gravado; enquanto a
     * captação está aberta (ou se faltar a gravação), é calculado na hora com as cotas de hoje.
     */
    private List<Posicionado> posicionar(Servico servico, List<Candidato> candidatos) {
        boolean gravado = servico.status() != StatusServico.CAPTACAO_ABERTA
                && candidatos.stream().allMatch(c -> c.posicao() != null && c.cotasMesAtual() != null);
        if (gravado) {
            return candidatos.stream()
                    .map(c -> new Posicionado(c, c.posicao(), c.cotasMesAnterior(), c.cotasMesAtual()))
                    .sorted(Comparator.comparingLong((Posicionado p) -> p.candidato().vagaId())
                            .thenComparingInt(Posicionado::posicao))
                    .toList();
        }

        var atual = Calendario.mesDe(Instant.now());
        var anterior = atual.minusMonths(1);
        var ids = candidatos.stream().map(Candidato::policialId).distinct().toList();
        var cotasAnterior = cotasNoMes(ids, anterior);
        var cotasAtual = cotasNoMes(ids, atual);

        List<Posicionado> resultado = new ArrayList<>();
        var porVaga = candidatos.stream().collect(Collectors.groupingBy(Candidato::vagaId));
        porVaga.keySet().stream().sorted().forEach(vagaId -> {
            Map<String, Candidato> porMatricula = porVaga.get(vagaId).stream()
                    .collect(Collectors.toMap(Candidato::matricula, Function.identity()));
            var entradas = porVaga.get(vagaId).stream()
                    .map(c -> new CandidatoRanking(c.matricula(),
                            cotasAnterior.getOrDefault(c.policialId(), BigDecimal.ZERO),
                            cotasAtual.getOrDefault(c.policialId(), BigDecimal.ZERO),
                            c.dataAdmissao()))
                    .toList();
            var ordenados = Ranking.ordenar(entradas, Ranking.MENOS_SVG_PRIMEIRO);
            for (int i = 0; i < ordenados.size(); i++) {
                var entrada = ordenados.get(i);
                resultado.add(new Posicionado(porMatricula.get(entrada.matricula()), i + 1,
                        entrada.cotasMesAnterior(), entrada.cotasMesAtual()));
            }
        });
        return resultado;
    }

    private Map<Long, BigDecimal> cotasNoMes(List<Long> policialIds, YearMonth mes) {
        return policiais.cotasNoMes(policialIds, mes.toString(), Calendario.inicioDoMes(mes), Calendario.fimDoMes(mes));
    }

    private Servico carregar(Jwt jwt, long id, boolean travar) {
        var policial = identidade.policial(jwt);
        var escopo = identidade.escopo(jwt, policial, IdentidadeService.PERFIL_CAPTACAO);
        var servico = (travar ? servicos.buscarParaAtualizar(id) : servicos.buscar(id))
                .orElseThrow(() -> ApiException.naoEncontrado("Serviço não encontrado"));
        if (!escopo.permite(servico.unidadeId())) {
            throw ApiException.proibido("O serviço pertence a outra unidade");
        }
        return servico;
    }

    private static ServicoCaptacao resumo(Servico servico, List<Vaga> vagas, Totais totais) {
        return new ServicoCaptacao(
                servico.id(),
                servico.ordemServico(),
                servico.titulo(),
                servico.local(),
                servico.inicio(),
                servico.fim(),
                servico.horasContadas(),
                CalculoCota.cotas(servico.horasContadas()),
                servico.captacaoFechaEm(),
                servico.status(),
                vagas.stream().map(VagaServico::de).toList(),
                totais == null ? 0 : totais.candidatos(),
                totais == null ? 0 : totais.selecionados());
    }

    private static CandidatoCaptacao candidato(Posicionado p) {
        var c = p.candidato();
        return new CandidatoCaptacao(c.candidaturaId(), c.vagaId(), c.matricula(), c.nome(), c.postoGraduacao(),
                p.cotasMesAnterior(), p.cotasMesAtual(), c.dataAdmissao(), p.posicao(), c.status(), c.situacaoExecucao());
    }

    private record Posicionado(Candidato candidato, int posicao, BigDecimal cotasMesAnterior, BigDecimal cotasMesAtual) {
    }
}
