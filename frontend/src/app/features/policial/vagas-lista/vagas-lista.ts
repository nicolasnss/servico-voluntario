import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { SvgApi } from '../../../core/api/svg-api';
import { ServicoResumo } from '../../../core/models';
import { ROTULO_STATUS_SERVICO, Rotulo } from '../../../shared/rotulos';

function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();
}

@Component({
  selector: 'app-vagas-lista',
  imports: [RouterLink, DatePipe, DecimalPipe, CurrencyPipe],
  templateUrl: './vagas-lista.html',
  styleUrl: './vagas-lista.scss',
})
export class VagasLista {
  private readonly api = inject(SvgApi);

  protected readonly busca = signal('');
  protected readonly somenteDisponiveis = signal(false);
  protected readonly rotuloStatus = ROTULO_STATUS_SERVICO;

  private readonly resultado = toSignal(
    this.api.listarServicos().pipe(
      map((dados) => ({ dados, erro: false })),
      catchError(() => of({ dados: [] as ServicoResumo[], erro: true })),
    ),
    { initialValue: null },
  );

  protected readonly carregando = computed(() => this.resultado() === null);
  protected readonly erro = computed(() => this.resultado()?.erro ?? false);

  protected readonly servicos = computed(() => {
    const termo = normalizar(this.busca());
    return (this.resultado()?.dados ?? []).filter((s) => {
      if (this.somenteDisponiveis() && !this.disponivel(s)) {
        return false;
      }
      return !termo || normalizar(`${s.titulo} ${s.local} ${s.unidade}`).includes(termo);
    });
  });

  protected disponivel(servico: ServicoResumo): boolean {
    return servico.status === 'CAPTACAO_ABERTA' && servico.elegibilidade.elegivel && !servico.jaCandidatou;
  }

  protected situacao(servico: ServicoResumo): Rotulo | null {
    if (servico.jaCandidatou) {
      return { texto: 'Candidatura enviada', classe: 'selo--ok' };
    }
    if (!servico.elegibilidade.elegivel) {
      return { texto: 'Indisponível para você', classe: 'selo--erro' };
    }
    if (servico.status === 'CAPTACAO_ABERTA') {
      return { texto: 'Você pode se candidatar', classe: 'selo--ok' };
    }
    return null;
  }

  protected totalDeVagas(servico: ServicoResumo): number {
    return servico.vagas.reduce((total, v) => total + v.quantidade, 0);
  }
}
