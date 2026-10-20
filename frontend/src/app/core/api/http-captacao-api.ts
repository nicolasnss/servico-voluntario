import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CandidatoCaptacao, SelecaoCaptacao, ServicoCaptacao, SituacaoExecucao } from '../models';
import { CaptacaoApi } from './captacao-api';

@Injectable()
export class HttpCaptacaoApi extends CaptacaoApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  listarServicos(): Observable<ServicoCaptacao[]> {
    return this.http.get<ServicoCaptacao[]>(`${this.base}/captacao/servicos`);
  }

  obterServico(id: number): Observable<ServicoCaptacao> {
    return this.http.get<ServicoCaptacao>(`${this.base}/captacao/servicos/${id}`);
  }

  candidatos(servicoId: number): Observable<CandidatoCaptacao[]> {
    return this.http.get<CandidatoCaptacao[]>(`${this.base}/captacao/servicos/${servicoId}/ranking`);
  }

  abrirCaptacao(servicoId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/captacao/servicos/${servicoId}/abrir`, null);
  }

  encerrarCaptacao(servicoId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/captacao/servicos/${servicoId}/encerrar`, null);
  }

  selecionar(servicoId: number, selecao: SelecaoCaptacao): Observable<void> {
    return this.http.post<void>(`${this.base}/captacao/servicos/${servicoId}/selecionar`, selecao);
  }

  lancarExecucao(candidaturaId: number, situacao: SituacaoExecucao): Observable<void> {
    return this.http.post<void>(`${this.base}/execucoes/${candidaturaId}`, { situacao });
  }
}
