import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';

import { SvgApi } from '../../../core/api/svg-api';
import { CotaMensal } from '../../../core/models';

function anoMesDe(data: Date): string {
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}`;
}

@Component({
  selector: 'app-minha-cota',
  imports: [DatePipe, DecimalPipe, CurrencyPipe],
  templateUrl: './minha-cota.html',
  styleUrl: './minha-cota.scss',
})
export class MinhaCota {
  private readonly api = inject(SvgApi);

  protected readonly anoMes = signal(anoMesDe(new Date()));
  protected readonly primeiroDia = computed(() => {
    const [ano, mes] = this.anoMes().split('-').map(Number);
    return new Date(ano, mes - 1, 1);
  });

  private readonly resultado = toSignal(
    toObservable(this.anoMes).pipe(
      switchMap((anoMes) =>
        this.api.minhaCota(anoMes).pipe(
          map((cota) => ({ cota, erro: false })),
          catchError(() => of({ cota: null as CotaMensal | null, erro: true })),
        ),
      ),
    ),
    { initialValue: null },
  );

  protected readonly carregando = computed(() => this.resultado() === null);
  protected readonly erro = computed(() => this.resultado()?.erro ?? false);
  protected readonly cota = computed(() => this.resultado()?.cota ?? null);

  protected mudarMes(delta: number): void {
    const base = this.primeiroDia();
    this.anoMes.set(anoMesDe(new Date(base.getFullYear(), base.getMonth() + delta, 1)));
  }
}
