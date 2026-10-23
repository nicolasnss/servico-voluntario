package com.nicolas.servicovoluntario.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nicolas.servicovoluntario.aplicacao.CandidaturaService;
import com.nicolas.servicovoluntario.aplicacao.Dtos.CandidaturaResumo;
import com.nicolas.servicovoluntario.aplicacao.Dtos.CotaMensal;
import com.nicolas.servicovoluntario.aplicacao.Dtos.NovaCandidatura;
import com.nicolas.servicovoluntario.aplicacao.Dtos.ServicoResumo;
import com.nicolas.servicovoluntario.aplicacao.ServicoService;

import jakarta.validation.Valid;

/** Telas do policial: vagas, candidaturas e cota. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('POLICIAL')")
class PolicialController {

    private final ServicoService servicos;
    private final CandidaturaService candidaturas;

    PolicialController(ServicoService servicos, CandidaturaService candidaturas) {
        this.servicos = servicos;
        this.candidaturas = candidaturas;
    }

    @GetMapping("/servicos")
    List<ServicoResumo> listarServicos(@AuthenticationPrincipal Jwt jwt) {
        return servicos.listar(jwt);
    }

    @GetMapping("/servicos/{id}")
    ServicoResumo obterServico(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return servicos.obter(jwt, id);
    }

    @PostMapping("/servicos/{id}/candidaturas")
    @ResponseStatus(HttpStatus.CREATED)
    CandidaturaResumo candidatar(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                 @Valid @RequestBody NovaCandidatura comando) {
        return candidaturas.candidatar(jwt, id, comando);
    }

    @GetMapping("/minhas-candidaturas")
    List<CandidaturaResumo> minhasCandidaturas(@AuthenticationPrincipal Jwt jwt) {
        return candidaturas.minhas(jwt);
    }

    @DeleteMapping("/candidaturas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        candidaturas.cancelar(jwt, id);
    }

    @GetMapping("/minha-cota")
    CotaMensal minhaCota(@AuthenticationPrincipal Jwt jwt, @RequestParam String anoMes) {
        return candidaturas.minhaCota(jwt, anoMes);
    }
}
