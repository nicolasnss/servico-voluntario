import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CandidaturaResumo, CotaMensal, NovaCandidatura, ServicoResumo } from '../models';
import { SvgApi } from './svg-api';

@Injectable()
export class HttpSvgApi extends SvgApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listarServicos(): Observable<ServicoResumo[]> {
    return this.http.get<ServicoResumo[]>(`${this.base}/servicos`);
  }

  obterServico(id: number): Observable<ServicoResumo> {
    return this.http.get<ServicoResumo>(`${this.base}/servicos/${id}`);
  }

  candidatar(servicoId: number, comando: NovaCandidatura): Observable<CandidaturaResumo> {
    return this.http.post<CandidaturaResumo>(`${this.base}/servicos/${servicoId}/candidaturas`, comando);
  }

  minhasCandidaturas(): Observable<CandidaturaResumo[]> {
    return this.http.get<CandidaturaResumo[]>(`${this.base}/minhas-candidaturas`);
  }

  cancelarCandidatura(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/candidaturas/${id}`);
  }

  minhaCota(anoMes: string): Observable<CotaMensal> {
    return this.http.get<CotaMensal>(`${this.base}/minha-cota`, { params: new HttpParams().set('anoMes', anoMes) });
  }
}
