package com.nicolas.servicovoluntario.regras;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class Ranking {

    /**
     * PROVISÓRIO: o art. 16 da Portaria PMDF 1.280/2022 não foi consultado. A ficha de inscrição (Anexo I)
     * indica que a seleção considera a quantidade de SVG do mês anterior e do mês atual; aqui, quem tem
     * menos cotas tem prioridade, com desempate pelo mês atual, antiguidade e matrícula.
     */
    public static final Comparator<CandidatoRanking> MENOS_SVG_PRIMEIRO = Comparator
            .comparing((CandidatoRanking c) -> c.cotasMesAnterior().add(c.cotasMesAtual()))
            .thenComparing(CandidatoRanking::cotasMesAtual)
            .thenComparing(CandidatoRanking::dataAdmissao)
            .thenComparing(CandidatoRanking::matricula);

    private Ranking() {
    }

    public static List<CandidatoRanking> ordenar(Collection<CandidatoRanking> candidatos,
                                                 Comparator<CandidatoRanking> criterio) {
        return candidatos.stream().sorted(criterio).toList();
    }
}
