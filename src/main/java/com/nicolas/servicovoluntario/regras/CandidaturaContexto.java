package com.nicolas.servicovoluntario.regras;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Estado necessário para decidir se um policial pode se candidatar a uma vaga.
 *
 * @param circulosPermitidos         vazio significa que a vaga aceita qualquer círculo
 * @param outrosServicosSelecionados SVGs já selecionados para o mesmo policial
 * @param cotasPlanejadasNoMes       cotas já selecionadas no mês do serviço, sem contar este
 * @param tetoIndividualMensal       nulo quando não há teto individual configurado
 */
public record CandidaturaContexto(
        Periodo servico,
        int horasContadas,
        Circulo circuloPolicial,
        Set<Circulo> circulosPermitidos,
        List<Periodo> escalaRegular,
        List<Periodo> afastamentos,
        List<Periodo> bloqueios,
        List<Periodo> outrosServicosSelecionados,
        boolean impedidoPorFuncaoEspecial,
        BigDecimal cotasPlanejadasNoMes,
        BigDecimal tetoIndividualMensal) {

    public CandidaturaContexto {
        circulosPermitidos = Set.copyOf(circulosPermitidos);
        escalaRegular = List.copyOf(escalaRegular);
        afastamentos = List.copyOf(afastamentos);
        bloqueios = List.copyOf(bloqueios);
        outrosServicosSelecionados = List.copyOf(outrosServicosSelecionados);
    }
}
