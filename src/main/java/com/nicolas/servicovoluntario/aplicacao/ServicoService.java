package com.nicolas.servicovoluntario.aplicacao;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nicolas.servicovoluntario.aplicacao.Dtos.Elegibilidade;
import com.nicolas.servicovoluntario.aplicacao.Dtos.ServicoResumo;
import com.nicolas.servicovoluntario.aplicacao.Dtos.VagaServico;
import com.nicolas.servicovoluntario.dominio.Servico;
import com.nicolas.servicovoluntario.dominio.StatusServico;
import com.nicolas.servicovoluntario.dominio.Vaga;
import com.nicolas.servicovoluntario.persistencia.CandidaturaRepository;
import com.nicolas.servicovoluntario.persistencia.ServicoRepository;
import com.nicolas.servicovoluntario.regras.CalculoCota;

/** Serviços como o policial os enxerga: com a elegibilidade dele já calculada. */
@Service
@Transactional(readOnly = true)
public class ServicoService {

    /** Rascunhos ainda não foram publicados; serviços realizados ou cancelados não aceitam mais ação. */
    private static final List<StatusServico> VISIVEIS = List.of(
            StatusServico.CAPTACAO_ABERTA, StatusServico.CAPTACAO_ENCERRADA, StatusServico.SELECIONADO);

    private final IdentidadeService identidade;
    private final ServicoRepository servicos;
    private final CandidaturaRepository candidaturas;
    private final ElegibilidadeService elegibilidade;
    private final Valores valores;

    public ServicoService(IdentidadeService identidade, ServicoRepository servicos, CandidaturaRepository candidaturas,
                          ElegibilidadeService elegibilidade, Valores valores) {
        this.identidade = identidade;
        this.servicos = servicos;
        this.candidaturas = candidaturas;
        this.elegibilidade = elegibilidade;
        this.valores = valores;
    }

    public List<ServicoResumo> listar(Jwt jwt) {
        var policial = identidade.policial(jwt);
        var lista = servicos.listarPorStatus(VISIVEIS);
        var vagas = servicos.vagasDe(lista.stream().map(Servico::id).toList());
        var avaliacao = elegibilidade.para(policial);
        var jaCandidatou = candidaturas.servicosComCandidatura(policial.id());
        var calculadora = valores.calculadora();
        return lista.stream()
                .map(s -> resumo(s, vagas.getOrDefault(s.id(), List.of()), avaliacao, jaCandidatou.contains(s.id()),
                        calculadora))
                .toList();
    }

    public ServicoResumo obter(Jwt jwt, long id) {
        var policial = identidade.policial(jwt);
        var servico = servicos.buscar(id)
                .filter(s -> VISIVEIS.contains(s.status()))
                .orElseThrow(() -> ApiException.naoEncontrado("Serviço não encontrado"));
        return resumo(servico, servicos.vagasDe(id), elegibilidade.para(policial),
                candidaturas.servicosComCandidatura(policial.id()).contains(id), valores.calculadora());
    }

    private static ServicoResumo resumo(Servico servico, List<Vaga> vagas, ElegibilidadeService.Avaliacao avaliacao,
                                        boolean jaCandidatou, Valores.Calculadora calculadora) {
        var violacoes = avaliacao.avaliar(servico, vagas);
        return new ServicoResumo(
                servico.id(),
                servico.ordemServico(),
                servico.titulo(),
                servico.local(),
                servico.unidade(),
                servico.inicio(),
                servico.fim(),
                servico.horasContadas(),
                CalculoCota.cotas(servico.horasContadas()),
                calculadora.valor(servico),
                servico.captacaoFechaEm(),
                servico.status(),
                vagas.stream().map(VagaServico::de).toList(),
                new Elegibilidade(violacoes.isEmpty(), violacoes),
                jaCandidatou);
    }
}
