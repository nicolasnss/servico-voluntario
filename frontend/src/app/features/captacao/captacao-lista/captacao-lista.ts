import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { CaptacaoApi } from '../../../core/api/captacao-api';
import { ServicoCaptacao } from '../../../core/models';
import { ROTULO_STATUS_SERVICO } from '../../../shared/rotulos';

@Component({
  selector: 'app-captacao-lista',
  imports: [RouterLink, DatePipe],
  templateUrl: './captacao-lista.html',
  styleUrl: './captacao-lista.scss',
})
export class CaptacaoLista {
  private readonly api = inject(CaptacaoApi);

  protected readonly rotuloStatus = ROTULO_STATUS_SERVICO;

  private readonly resultado = toSignal(
    this.api.listarServicos().pipe(
      map((dados) => ({ dados, erro: false })),
      catchError(() => of({ dados: [] as ServicoCaptacao[], erro: true })),
    ),
    { initialValue: null },
  );

  protected readonly carregando = computed(() => this.resultado() === null);
  protected readonly erro = computed(() => this.resultado()?.erro ?? false);
  protected readonly servicos = computed(() => this.resultado()?.dados ?? []);

  protected totalDeVagas(servico: ServicoCaptacao): number {
    return servico.vagas.reduce((total, v) => total + v.quantidade, 0);
  }

  protected acao(servico: ServicoCaptacao): string {
    switch (servico.status) {
      case 'RASCUNHO':
        return 'Abrir captação';
      case 'CAPTACAO_ABERTA':
        return 'Acompanhar captação';
      case 'CAPTACAO_ENCERRADA':
        return 'Selecionar';
      case 'SELECIONADO':
        return 'Lançar presenças';
      default:
        return 'Ver detalhes';
    }
  }
}
