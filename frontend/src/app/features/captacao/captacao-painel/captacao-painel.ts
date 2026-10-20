import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, forkJoin } from 'rxjs';

import { CaptacaoApi } from '../../../core/api/captacao-api';
import { CandidatoCaptacao, ServicoCaptacao, SituacaoExecucao, VagaServico } from '../../../core/models';
import { mensagemDeErro } from '../../../shared/erros';
import { ROTULO_SITUACAO_EXECUCAO, ROTULO_STATUS_CANDIDATURA, ROTULO_STATUS_SERVICO } from '../../../shared/rotulos';

export const MINIMO_JUSTIFICATIVA = 10;

interface GrupoVaga {
  vaga: VagaServico;
  candidatos: CandidatoCaptacao[];
}

@Component({
  selector: 'app-captacao-painel',
  imports: [RouterLink, DatePipe, DecimalPipe],
  templateUrl: './captacao-painel.html',
  styleUrl: './captacao-painel.scss',
})
export class CaptacaoPainel {
  /** Vem do parâmetro :id da rota (withComponentInputBinding). */
  readonly id = input.required<string>();

  private readonly api = inject(CaptacaoApi);

  protected readonly rotuloStatus = ROTULO_STATUS_SERVICO;
  protected readonly rotuloCandidatura = ROTULO_STATUS_CANDIDATURA;
  protected readonly rotuloExecucao = ROTULO_SITUACAO_EXECUCAO;
  protected readonly minimoJustificativa = MINIMO_JUSTIFICATIVA;

  protected readonly servico = signal<ServicoCaptacao | null>(null);
  protected readonly candidatos = signal<CandidatoCaptacao[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly confirmandoEncerramento = signal(false);

  protected readonly selecionados = signal<ReadonlySet<number>>(new Set());
  protected readonly justificativa = signal('');

  protected readonly grupos = computed<GrupoVaga[]>(() =>
    (this.servico()?.vagas ?? []).map((vaga) => ({
      vaga,
      candidatos: this.candidatos()
        .filter((c) => c.vagaId === vaga.id)
        .sort((a, b) => a.posicao - b.posicao),
    })),
  );

  /** Seleção que difere do ranking (menos SVG primeiro) precisa ser justificada. */
  protected readonly divergiu = computed(() =>
    this.grupos().some((grupo) => {
      const sugeridos = this.sugeridosDe(grupo);
      const escolhidos = grupo.candidatos.filter((c) => this.selecionados().has(c.candidaturaId));
      return escolhidos.length !== sugeridos.size || escolhidos.some((c) => !sugeridos.has(c.candidaturaId));
    }),
  );

  protected readonly excedeu = computed(() =>
    this.grupos().some((grupo) => this.qtdSelecionados(grupo) > grupo.vaga.quantidade),
  );

  protected readonly justificativaValida = computed(
    () => this.justificativa().trim().length >= MINIMO_JUSTIFICATIVA,
  );

  protected readonly podeConfirmar = computed(
    () =>
      !this.ocupado() &&
      !this.excedeu() &&
      this.selecionados().size > 0 &&
      (!this.divergiu() || this.justificativaValida()),
  );

  protected readonly selecionadosParaExecucao = computed(() =>
    this.candidatos().filter((c) => c.status === 'SELECIONADA'),
  );

  protected readonly naoSelecionados = computed(() =>
    this.candidatos().filter((c) => c.status === 'NAO_SELECIONADA'),
  );

  constructor() {
    toObservable(this.id)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.carregar());
  }

  protected qtdSelecionados(grupo: GrupoVaga): number {
    return grupo.candidatos.filter((c) => this.selecionados().has(c.candidaturaId)).length;
  }

  protected dentroDasVagas(grupo: GrupoVaga, candidato: CandidatoCaptacao): boolean {
    return candidato.posicao <= grupo.vaga.quantidade;
  }

  protected alternar(candidaturaId: number, marcado: boolean): void {
    const atual = new Set(this.selecionados());
    if (marcado) {
      atual.add(candidaturaId);
    } else {
      atual.delete(candidaturaId);
    }
    this.selecionados.set(atual);
  }

  protected restaurarRanking(): void {
    this.selecionados.set(this.sugestaoDoRanking());
    this.justificativa.set('');
  }

  protected abrir(): void {
    this.executar(this.api.abrirCaptacao(this.servicoId()));
  }

  protected encerrar(): void {
    this.confirmandoEncerramento.set(false);
    this.executar(this.api.encerrarCaptacao(this.servicoId()));
  }

  protected confirmar(): void {
    if (!this.podeConfirmar()) {
      return;
    }
    this.executar(
      this.api.selecionar(this.servicoId(), {
        candidaturaIds: [...this.selecionados()],
        justificativa: this.divergiu() ? this.justificativa().trim() : null,
      }),
    );
  }

  protected lancar(candidato: CandidatoCaptacao, situacao: SituacaoExecucao): void {
    this.executar(this.api.lancarExecucao(candidato.candidaturaId, situacao));
  }

  private servicoId(): number {
    return Number(this.id());
  }

  private sugeridosDe(grupo: GrupoVaga): Set<number> {
    return new Set(grupo.candidatos.slice(0, grupo.vaga.quantidade).map((c) => c.candidaturaId));
  }

  private sugestaoDoRanking(): Set<number> {
    return new Set(this.grupos().flatMap((grupo) => [...this.sugeridosDe(grupo)]));
  }

  private executar(operacao: Observable<void>): void {
    this.ocupado.set(true);
    this.erro.set(null);
    operacao.subscribe({
      next: () => {
        this.ocupado.set(false);
        this.carregar();
      },
      error: (e: unknown) => {
        this.ocupado.set(false);
        this.erro.set(mensagemDeErro(e));
      },
    });
  }

  private carregar(): void {
    forkJoin([this.api.obterServico(this.servicoId()), this.api.candidatos(this.servicoId())]).subscribe({
      next: ([servico, candidatos]) => {
        this.servico.set(servico);
        this.candidatos.set(candidatos);
        this.selecionados.set(this.sugestaoDoRanking());
        this.justificativa.set('');
        this.carregando.set(false);
      },
      error: (e: unknown) => {
        this.erro.set(mensagemDeErro(e));
        this.carregando.set(false);
      },
    });
  }
}
