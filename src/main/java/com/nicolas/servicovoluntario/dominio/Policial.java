package com.nicolas.servicovoluntario.dominio;

import java.time.LocalDate;

import com.nicolas.servicovoluntario.regras.Circulo;

public record Policial(
        long id,
        String matricula,
        String nome,
        String postoGraduacao,
        Circulo circulo,
        LocalDate dataAdmissao,
        long unidadeId) {
}
