import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';

import { SvgApi } from '../../../core/api/svg-api';
import { ServicoResumo } from '../../../core/models';
import { mensagemDeErro } from '../../../shared/erros';
import { ROTULO_STATUS_SERVICO } from '../../../shared/rotulos';

@Component({
  selector: 'app-vaga-detalhe',
  imports: [RouterLink, DatePipe, DecimalPipe, CurrencyPipe],
  templateUrl: './vaga-detalhe.html',
  styleUrl: './vaga-detalhe.scss',
})
export class VagaDetalhe {
  /** Vem do parâmetro :id da rota (withComponentInputBinding). */
  readonly id = input.required<string>();

  private readonly api = inject(SvgApi);
  private readonly router = inject(Router);

  protected readonly rotuloStatus = ROTULO_STATUS_SERVICO;
  protected readonly vagaEscolhida = signal<number | null>(null);
  protected readonly declaracaoAceita = signal(false);
  protected readonly enviando = signal(false);
  protected readonly erroEnvio = signal<string | null>(null);

  private readonly resultado = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) =>
        this.api.obterServico(Number(id)).pipe(
          map((dados) => ({ dados, erro: false })),
          catchError(() => of({ dados: null as ServicoResumo | null, erro: true })),
        ),
      ),
    ),
    { initialValue: null },
  );

  protected readonly carregando = computed(() => this.resultado() === null);
  protected readonly erro = computed(() => this.resultado()?.erro ?? false);
  protected readonly servico = computed(() => this.resultado()?.dados ?? null);

  /** Com uma única vaga ela já vem escolhida; com várias, o policial decide. */
  protected readonly vagaSelecionada = computed(() => {
    const vagas = this.servico()?.vagas ?? [];
    return this.vagaEscolhida() ?? (vagas.length === 1 ? vagas[0].id : null);
  });

  protected readonly podeCandidatar = computed(() => {
    const servico = this.servico();
    return (
      !!servico &&
      servico.status === 'CAPTACAO_ABERTA' &&
      servico.elegibilidade.elegivel &&
      !servico.jaCandidatou
    );
  });

  protected readonly podeEnviar = computed(
    () => this.podeCandidatar() && this.vagaSelecionada() !== null && this.declaracaoAceita() && !this.enviando(),
  );

  protected enviar(): void {
    const servico = this.servico();
    const vagaId = this.vagaSelecionada();
    if (!servico || vagaId === null || !this.podeEnviar()) {
      return;
    }
    this.enviando.set(true);
    this.erroEnvio.set(null);
    this.api.candidatar(servico.id, { vagaId, declaracaoArt5Aceita: this.declaracaoAceita() }).subscribe({
      next: () => void this.router.navigate(['/minhas-candidaturas']),
      error: (erro: unknown) => {
        this.erroEnvio.set(mensagemDeErro(erro));
        this.enviando.set(false);
      },
    });
  }
}
