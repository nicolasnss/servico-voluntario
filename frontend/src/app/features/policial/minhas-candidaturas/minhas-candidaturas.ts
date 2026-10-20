import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SvgApi } from '../../../core/api/svg-api';
import { CandidaturaResumo } from '../../../core/models';
import { mensagemDeErro } from '../../../shared/erros';
import { ROTULO_SITUACAO_EXECUCAO, ROTULO_STATUS_CANDIDATURA } from '../../../shared/rotulos';

@Component({
  selector: 'app-minhas-candidaturas',
  imports: [RouterLink, DatePipe, DecimalPipe, CurrencyPipe],
  templateUrl: './minhas-candidaturas.html',
  styleUrl: './minhas-candidaturas.scss',
})
export class MinhasCandidaturas {
  private readonly api = inject(SvgApi);

  protected readonly rotuloStatus = ROTULO_STATUS_CANDIDATURA;
  protected readonly rotuloExecucao = ROTULO_SITUACAO_EXECUCAO;

  protected readonly candidaturas = signal<CandidaturaResumo[] | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly confirmandoCancelamento = signal<number | null>(null);

  constructor() {
    this.carregar();
  }

  protected podeCancelar(c: CandidaturaResumo): boolean {
    return (
      (c.status === 'CANDIDATA' || c.status === 'SELECIONADA') &&
      c.situacaoExecucao === null &&
      new Date(c.inicio) > new Date()
    );
  }

  protected cancelar(c: CandidaturaResumo): void {
    this.erro.set(null);
    this.api.cancelarCandidatura(c.id).subscribe({
      next: () => {
        this.confirmandoCancelamento.set(null);
        this.carregar();
      },
      error: (e: unknown) => {
        this.confirmandoCancelamento.set(null);
        this.erro.set(mensagemDeErro(e));
      },
    });
  }

  private carregar(): void {
    this.api.minhasCandidaturas().subscribe({
      next: (dados) => this.candidaturas.set(dados),
      error: (e: unknown) => {
        this.candidaturas.set([]);
        this.erro.set(mensagemDeErro(e));
      },
    });
  }
}
