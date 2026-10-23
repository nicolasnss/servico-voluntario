package com.nicolas.servicovoluntario.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nicolas.servicovoluntario.aplicacao.CaptacaoService;
import com.nicolas.servicovoluntario.aplicacao.Dtos.CandidatoCaptacao;
import com.nicolas.servicovoluntario.aplicacao.Dtos.LancamentoExecucao;
import com.nicolas.servicovoluntario.aplicacao.Dtos.SelecaoCaptacao;
import com.nicolas.servicovoluntario.aplicacao.Dtos.ServicoCaptacao;

import jakarta.validation.Valid;

/** Telas do responsável pela captação da unidade. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('RESP_CAPTACAO')")
class CaptacaoController {

    private final CaptacaoService captacao;

    CaptacaoController(CaptacaoService captacao) {
        this.captacao = captacao;
    }

    @GetMapping("/captacao/servicos")
    List<ServicoCaptacao> listar(@AuthenticationPrincipal Jwt jwt) {
        return captacao.listar(jwt);
    }

    @GetMapping("/captacao/servicos/{id}")
    ServicoCaptacao obter(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return captacao.obter(jwt, id);
    }

    @GetMapping("/captacao/servicos/{id}/ranking")
    List<CandidatoCaptacao> ranking(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return captacao.ranking(jwt, id);
    }

    @PostMapping("/captacao/servicos/{id}/abrir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void abrir(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        captacao.abrir(jwt, id);
    }

    @PostMapping("/captacao/servicos/{id}/encerrar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void encerrar(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        captacao.encerrar(jwt, id);
    }

    @PostMapping("/captacao/servicos/{id}/selecionar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void selecionar(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody SelecaoCaptacao selecao) {
        captacao.selecionar(jwt, id, selecao);
    }

    @PostMapping("/execucoes/{candidaturaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void lancarExecucao(@AuthenticationPrincipal Jwt jwt, @PathVariable long candidaturaId,
                        @Valid @RequestBody LancamentoExecucao lancamento) {
        captacao.lancarExecucao(jwt, candidaturaId, lancamento.situacao());
    }
}
