import { Observable } from 'rxjs';

import { CandidatoCaptacao, SelecaoCaptacao, ServicoCaptacao, SituacaoExecucao } from '../models';

/** Operações do responsável pela captação da unidade. */
export abstract class CaptacaoApi {
  abstract listarServicos(): Observable<ServicoCaptacao[]>;
  abstract obterServico(id: number): Observable<ServicoCaptacao>;
  abstract candidatos(servicoId: number): Observable<CandidatoCaptacao[]>;
  abstract abrirCaptacao(servicoId: number): Observable<void>;
  abstract encerrarCaptacao(servicoId: number): Observable<void>;
  abstract selecionar(servicoId: number, selecao: SelecaoCaptacao): Observable<void>;
  abstract lancarExecucao(candidaturaId: number, situacao: SituacaoExecucao): Observable<void>;
}
