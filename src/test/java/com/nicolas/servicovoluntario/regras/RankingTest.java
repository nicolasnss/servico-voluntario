package com.nicolas.servicovoluntario.regras;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class RankingTest {

    private static CandidatoRanking c(String matricula, String anterior, String atual, String admissao) {
        return new CandidatoRanking(matricula, new BigDecimal(anterior), new BigDecimal(atual), LocalDate.parse(admissao));
    }

    private static List<String> matriculas(List<CandidatoRanking> ordenados) {
        return ordenados.stream().map(CandidatoRanking::matricula).toList();
    }

    @Test
    void quemTemMenosSvgNoMesAnteriorEAtualTemPrioridade() {
        var ordenados = Ranking.ordenar(List.of(
                c("3", "10", "5", "2010-01-01"),
                c("1", "0", "0", "2020-01-01"),
                c("2", "4", "1", "2015-01-01")), Ranking.MENOS_SVG_PRIMEIRO);

        assertThat(matriculas(ordenados)).containsExactly("1", "2", "3");
    }

    @Test
    void empateNoTotalDesempataPeloMesAtualDepoisAntiguidadeDepoisMatricula() {
        var ordenados = Ranking.ordenar(List.of(
                c("4", "2", "3", "2012-01-01"),
                c("3", "3", "2", "2020-01-01"),
                c("2", "3", "2", "2010-01-01"),
                c("1", "3", "2", "2010-01-01")), Ranking.MENOS_SVG_PRIMEIRO);

        assertThat(matriculas(ordenados)).containsExactly("1", "2", "3", "4");
    }
}
