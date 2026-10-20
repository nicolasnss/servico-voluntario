import { Observable } from 'rxjs';

import { CandidaturaResumo, CotaMensal, NovaCandidatura, ServicoResumo } from '../models';

/** Contrato com o backend; a implementação em memória permite desenvolver a tela antes da API existir. */
export abstract class SvgApi {
  abstract listarServicos(): Observable<ServicoResumo[]>;
  abstract obterServico(id: number): Observable<ServicoResumo>;
  abstract candidatar(servicoId: number, comando: NovaCandidatura): Observable<CandidaturaResumo>;
  abstract minhasCandidaturas(): Observable<CandidaturaResumo[]>;
  abstract cancelarCandidatura(id: number): Observable<void>;
  abstract minhaCota(anoMes: string): Observable<CotaMensal>;
}
