package com.nicolas.servicovoluntario.aplicacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.nicolas.servicovoluntario.dominio.SituacaoExecucao;
import com.nicolas.servicovoluntario.dominio.StatusCandidatura;
import com.nicolas.servicovoluntario.dominio.StatusServico;
import com.nicolas.servicovoluntario.dominio.TipoFuncao;
import com.nicolas.servicovoluntario.dominio.Vaga;
import com.nicolas.servicovoluntario.regras.Violacao;

import jakarta.validation.constraints.NotNull;

/** Contrato JSON com o front; os nomes seguem {@code frontend/src/app/core/models.ts}. */
public final class Dtos {

    private Dtos() {
    }

    public record VagaServico(long id, String funcao, TipoFuncao tipoFuncao, int quantidade) {

        static VagaServico de(Vaga vaga) {
            return new VagaServico(vaga.id(), vaga.funcao(), vaga.tipoFuncao(), vaga.quantidade());
        }
    }

    public record Elegibilidade(boolean elegivel, List<Violacao> violacoes) {
    }

    public record ServicoResumo(
            long id,
            String ordemServico,
            String titulo,
            String local,
            String unidade,
            Instant inicio,
            Instant fim,
            int horasContadas,
            BigDecimal cotas,
            BigDecimal valor,
            Instant captacaoFechaEm,
            StatusServico status,
            List<VagaServico> vagas,
            Elegibilidade elegibilidade,
            boolean jaCandidatou) {
    }

    public record CandidaturaResumo(
            long id,
            long servicoId,
            String titulo,
            String local,
            Instant inicio,
            Instant fim,
            int horasContadas,
            BigDecimal cotas,
            BigDecimal valor,
            String funcao,
            StatusCandidatura status,
            Integer posicaoRanking,
            String justificativa,
            SituacaoExecucao situacaoExecucao) {
    }

    public record CotaMensal(
            String anoMes,
            BigDecimal planejadas,
            BigDecimal realizadas,
            BigDecimal tetoIndividual,
            BigDecimal valorPrevisto) {
    }

    public record NovaCandidatura(@NotNull Long vagaId, boolean declaracaoArt5Aceita) {
    }

    public record ServicoCaptacao(
            long id,
            String ordemServico,
            String titulo,
            String local,
            Instant inicio,
            Instant fim,
            int horasContadas,
            BigDecimal cotas,
            Instant captacaoFechaEm,
            StatusServico status,
            List<VagaServico> vagas,
            int totalCandidatos,
            int totalSelecionados) {
    }

    public record CandidatoCaptacao(
            long candidaturaId,
            long vagaId,
            String matricula,
            String nome,
            String postoGraduacao,
            BigDecimal cotasMesAnterior,
            BigDecimal cotasMesAtual,
            LocalDate dataAdmissao,
            int posicao,
            StatusCandidatura status,
            SituacaoExecucao situacaoExecucao) {
    }

    public record SelecaoCaptacao(@NotNull List<Long> candidaturaIds, String justificativa) {
    }

    public record LancamentoExecucao(@NotNull SituacaoExecucao situacao) {
    }
}
