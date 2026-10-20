import { Injectable } from '@angular/core';
import { Observable, delay, of, throwError } from 'rxjs';

import {
  CandidatoCaptacao,
  SelecaoCaptacao,
  ServicoCaptacao,
  SituacaoExecucao,
  StatusCandidatura,
  StatusServico,
  VagaServico,
} from '../models';
import { CaptacaoApi } from './captacao-api';
import { emDias } from './mock-svg-api';

const LATENCIA_MS = 250;
const MINIMO_JUSTIFICATIVA = 10;

type CandidatoSemPosicao = Omit<CandidatoCaptacao, 'posicao'>;

function candidato(
  candidaturaId: number,
  vagaId: number,
  matricula: string,
  nome: string,
  postoGraduacao: string,
  cotasMesAnterior: number,
  cotasMesAtual: number,
  dataAdmissao: string,
  status: StatusCandidatura = 'CANDIDATA',
): CandidatoSemPosicao {
  return {
    candidaturaId, vagaId, matricula, nome, postoGraduacao, cotasMesAnterior, cotasMesAtual, dataAdmissao,
    status, situacaoExecucao: null,
  };
}

/** Mesmo critério do Ranking.MENOS_SVG_PRIMEIRO do backend (provisório até a Portaria 1.280/2022). */
function compararRanking(a: CandidatoSemPosicao, b: CandidatoSemPosicao): number {
  return (
    a.cotasMesAnterior + a.cotasMesAtual - (b.cotasMesAnterior + b.cotasMesAtual) ||
    a.cotasMesAtual - b.cotasMesAtual ||
    a.dataAdmissao.localeCompare(b.dataAdmissao) ||
    a.matricula.localeCompare(b.matricula)
  );
}

function servico(
  id: number,
  titulo: string,
  local: string,
  dias: number,
  hora: number,
  horas: number,
  status: StatusServico,
  vagas: VagaServico[],
  ordemServico: string | null = null,
): ServicoCaptacao {
  return {
    id, ordemServico, titulo, local,
    inicio: emDias(dias, hora), fim: emDias(dias, hora + horas),
    horasContadas: horas, cotas: horas / 8,
    captacaoFechaEm: emDias(dias - 2, 18),
    status, vagas, totalCandidatos: 0, totalSelecionados: 0,
  };
}

@Injectable()
export class MockCaptacaoApi extends CaptacaoApi {
  private servicos: ServicoCaptacao[] = [
    servico(1, 'Policiamento de evento – Festival de Música', 'Parque da Cidade', 6, 14, 8, 'CAPTACAO_ENCERRADA', [
      { id: 11, funcao: 'Policial de execução', tipoFuncao: 'EXECUCAO', quantidade: 3 },
      { id: 12, funcao: 'Supervisão', tipoFuncao: 'SUPERVISAO', quantidade: 1 },
    ], 'OS 0412/2026'),
    servico(2, 'Reforço de policiamento – Estação de metrô', 'Estação Central', 4, 6, 6, 'CAPTACAO_ABERTA', [
      { id: 21, funcao: 'Policial de execução', tipoFuncao: 'EXECUCAO', quantidade: 2 },
    ]),
    servico(3, 'Policiamento de evento – Feira do Livro', 'Esplanada dos Ministérios', 2, 9, 8, 'SELECIONADO', [
      { id: 31, funcao: 'Policial de execução', tipoFuncao: 'EXECUCAO', quantidade: 2 },
    ]),
    servico(4, 'Segurança de evento esportivo', 'Estádio Nacional', 9, 15, 12, 'RASCUNHO', [
      { id: 41, funcao: 'Policial de execução', tipoFuncao: 'EXECUCAO', quantidade: 10 },
    ]),
  ];

  // Nomes e matrículas fictícios.
  private candidatosPorServico = new Map<number, CandidatoSemPosicao[]>([
    [1, [
      candidato(101, 11, '2001', 'Sd. Alves', 'Soldado', 0, 0, '2018-03-01'),
      candidato(102, 11, '2002', 'Cb. Barros', 'Cabo', 1, 0, '2012-05-10'),
      candidato(103, 11, '2003', 'Sd. Cunha', 'Soldado', 0, 1, '2020-01-15'),
      candidato(104, 11, '2004', 'Sgt. Dias', 'Sargento', 2, 2, '2008-07-01'),
      candidato(105, 11, '2005', 'Sd. Esteves', 'Soldado', 0, 0, '2015-09-20'),
      candidato(106, 11, '2006', 'Cb. Farias', 'Cabo', 3, 1, '2010-02-02'),
      candidato(107, 12, '2007', '3º Sgt. Gomes', '3º Sargento', 1, 1, '2009-04-04'),
      candidato(108, 12, '2008', 'Sgt. Hora', 'Sargento', 0, 2, '2007-11-11'),
    ]],
    [2, [
      candidato(201, 21, '2011', 'Sd. Ibarra', 'Soldado', 2, 0, '2019-08-08'),
      candidato(202, 21, '2012', 'Cb. Junqueira', 'Cabo', 0, 0, '2014-06-06'),
      candidato(203, 21, '2013', 'Sd. Klein', 'Soldado', 1, 1, '2021-02-02'),
      candidato(204, 21, '2014', 'Sd. Lopes', 'Soldado', 0, 3, '2017-12-12'),
    ]],
    [3, [
      candidato(301, 31, '2021', 'Cb. Moreira', 'Cabo', 0, 0, '2013-03-03', 'SELECIONADA'),
      candidato(302, 31, '2022', 'Sd. Nogueira', 'Soldado', 0, 1, '2016-10-10', 'SELECIONADA'),
      candidato(303, 31, '2023', 'Sd. Ortiz', 'Soldado', 2, 2, '2019-05-05', 'NAO_SELECIONADA'),
    ]],
  ]);

  listarServicos(): Observable<ServicoCaptacao[]> {
    return of(this.servicos.map((s) => this.comTotais(s))).pipe(delay(LATENCIA_MS));
  }

  obterServico(id: number): Observable<ServicoCaptacao> {
    const encontrado = this.servicos.find((s) => s.id === id);
    return encontrado
      ? of(this.comTotais(encontrado)).pipe(delay(LATENCIA_MS))
      : throwError(() => new Error('Serviço não encontrado'));
  }

  candidatos(servicoId: number): Observable<CandidatoCaptacao[]> {
    const servicoAlvo = this.servicos.find((s) => s.id === servicoId);
    if (!servicoAlvo) {
      return throwError(() => new Error('Serviço não encontrado'));
    }
    const lista = this.candidatosPorServico.get(servicoId) ?? [];
    const posicionados = servicoAlvo.vagas.flatMap((vaga) =>
      lista
        .filter((c) => c.vagaId === vaga.id)
        .sort(compararRanking)
        .map((c, indice) => ({ ...c, posicao: indice + 1 })),
    );
    return of(posicionados).pipe(delay(LATENCIA_MS));
  }

  abrirCaptacao(servicoId: number): Observable<void> {
    return this.mudarStatus(servicoId, 'RASCUNHO', 'CAPTACAO_ABERTA', 'Só é possível abrir a captação de um rascunho');
  }

  encerrarCaptacao(servicoId: number): Observable<void> {
    return this.mudarStatus(
      servicoId, 'CAPTACAO_ABERTA', 'CAPTACAO_ENCERRADA', 'A captação não está aberta',
    );
  }

  selecionar(servicoId: number, selecao: SelecaoCaptacao): Observable<void> {
    const alvo = this.servicos.find((s) => s.id === servicoId);
    if (!alvo) {
      return throwError(() => new Error('Serviço não encontrado'));
    }
    if (alvo.status !== 'CAPTACAO_ENCERRADA') {
      return throwError(() => new Error('A seleção só pode ser feita depois de encerrada a captação'));
    }

    const lista = this.candidatosPorServico.get(servicoId) ?? [];
    const escolhidos = new Set(selecao.candidaturaIds);
    let divergiu = false;

    for (const vaga of alvo.vagas) {
      const daVaga = lista.filter((c) => c.vagaId === vaga.id).sort(compararRanking);
      const selecionadosDaVaga = daVaga.filter((c) => escolhidos.has(c.candidaturaId));
      if (selecionadosDaVaga.length > vaga.quantidade) {
        return throwError(() => new Error(`A vaga “${vaga.funcao}” admite no máximo ${vaga.quantidade} selecionados`));
      }
      const sugeridos = new Set(daVaga.slice(0, vaga.quantidade).map((c) => c.candidaturaId));
      divergiu ||= selecionadosDaVaga.length !== sugeridos.size || selecionadosDaVaga.some((c) => !sugeridos.has(c.candidaturaId));
    }
    if (selecao.candidaturaIds.some((id) => !lista.some((c) => c.candidaturaId === id))) {
      return throwError(() => new Error('A seleção contém candidaturas que não pertencem a este serviço'));
    }
    if (divergiu && (selecao.justificativa ?? '').trim().length < MINIMO_JUSTIFICATIVA) {
      return throwError(() => new Error('Seleção diferente do ranking exige justificativa'));
    }

    for (const c of lista) {
      c.status = escolhidos.has(c.candidaturaId) ? 'SELECIONADA' : 'NAO_SELECIONADA';
    }
    alvo.status = 'SELECIONADO';
    return of(undefined).pipe(delay(LATENCIA_MS));
  }

  lancarExecucao(candidaturaId: number, situacao: SituacaoExecucao): Observable<void> {
    for (const [servicoId, lista] of this.candidatosPorServico) {
      const alvo = lista.find((c) => c.candidaturaId === candidaturaId);
      if (!alvo) {
        continue;
      }
      const dono = this.servicos.find((s) => s.id === servicoId)!;
      if (alvo.status !== 'SELECIONADA' || (dono.status !== 'SELECIONADO' && dono.status !== 'REALIZADO')) {
        return throwError(() => new Error('Só é possível lançar presença ou falta de quem foi selecionado'));
      }
      alvo.situacaoExecucao = situacao;
      const pendentes = lista.some((c) => c.status === 'SELECIONADA' && c.situacaoExecucao === null);
      dono.status = pendentes ? 'SELECIONADO' : 'REALIZADO';
      return of(undefined).pipe(delay(LATENCIA_MS));
    }
    return throwError(() => new Error('Candidatura não encontrada'));
  }

  private mudarStatus(id: number, de: StatusServico, para: StatusServico, erro: string): Observable<void> {
    const alvo = this.servicos.find((s) => s.id === id);
    if (!alvo) {
      return throwError(() => new Error('Serviço não encontrado'));
    }
    if (alvo.status !== de) {
      return throwError(() => new Error(erro));
    }
    alvo.status = para;
    return of(undefined).pipe(delay(LATENCIA_MS));
  }

  private comTotais(s: ServicoCaptacao): ServicoCaptacao {
    const lista = this.candidatosPorServico.get(s.id) ?? [];
    return {
      ...s,
      vagas: s.vagas.map((v) => ({ ...v })),
      totalCandidatos: lista.length,
      totalSelecionados: lista.filter((c) => c.status === 'SELECIONADA').length,
    };
  }
}
