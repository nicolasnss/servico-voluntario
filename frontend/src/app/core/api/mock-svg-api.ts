import { Injectable } from '@angular/core';
import { Observable, delay, of, throwError } from 'rxjs';

import {
  CandidaturaResumo,
  CotaMensal,
  Elegibilidade,
  NovaCandidatura,
  ServicoResumo,
  StatusServico,
  VagaServico,
  Violacao,
} from '../models';
import { SvgApi } from './svg-api';

const VALOR_HORA = 95; // R$ 760,00 / 8h (Decreto 39.627, art. 3º)
const LATENCIA_MS = 250;

function emDias(dias: number, hora: number, minuto = 0): string {
  const data = new Date();
  data.setDate(data.getDate() + dias);
  data.setHours(hora, minuto, 0, 0);
  return data.toISOString();
}

/** Mês (no fuso local) em que o serviço acontece, no formato AAAA-MM usado pela API. */
export function anoMesDe(iso: string): string {
  const data = new Date(iso);
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}`;
}

function servico(
  id: number,
  titulo: string,
  local: string,
  unidade: string,
  dias: number,
  horaInicio: number,
  horas: number,
  vagas: VagaServico[],
  opcoes: { status?: StatusServico; violacoes?: Violacao[]; ordemServico?: string } = {},
): ServicoResumo {
  const violacoes = opcoes.violacoes ?? [];
  const elegibilidade: Elegibilidade = { elegivel: violacoes.length === 0, violacoes };
  return {
    id,
    ordemServico: opcoes.ordemServico ?? null,
    titulo,
    local,
    unidade,
    inicio: emDias(dias, horaInicio),
    fim: emDias(dias, horaInicio + horas),
    horasContadas: horas,
    cotas: horas / 8,
    valor: horas * VALOR_HORA,
    captacaoFechaEm: emDias(dias - 2, 18),
    status: opcoes.status ?? 'CAPTACAO_ABERTA',
    vagas,
    elegibilidade,
    jaCandidatou: false,
  };
}

@Injectable()
export class MockSvgApi extends SvgApi {
  private servicos: ServicoResumo[] = [
    servico(1, 'Policiamento de evento – Festival de Música', 'Parque da Cidade', '1º BPM', 6, 14, 8, [
      { id: 11, funcao: 'Policial de execução', tipoFuncao: 'EXECUCAO', quantidade: 20 },
      { id: 12, funcao: 'Supervisão', tipoFuncao: 'SUPERVISAO', quantidade: 2 },
    ], { ordemServico: 'OS 0412/2026' }),
    servico(2, 'Reforço de policiamento – Estação de metrô', 'Estação Central', '2º BPM', 4, 6, 6, [
      { id: 21, funcao: 'Policial de execução', tipoFuncao: 'EXECUCAO', quantidade: 6 },
    ]),
    servico(3, 'Policiamento escolar – CEF 01', 'CEF 01 de Brasília', '1º BPM', 3, 7, 4, [
      { id: 31, funcao: 'Policial de execução', tipoFuncao: 'EXECUCAO', quantidade: 4 },
    ], {
      violacoes: [{ codigo: 'CHOQUE_ESCALA_REGULAR', mensagem: 'O serviço coincide com a escala ordinária do policial' }],
    }),
    servico(4, 'Segurança de evento esportivo', 'Estádio Nacional', '3º BPM', 9, 15, 12, [
      { id: 41, funcao: 'Policial de execução', tipoFuncao: 'EXECUCAO', quantidade: 40 },
    ], {
      violacoes: [{
        codigo: 'DESCANSO_INSUFICIENTE',
        mensagem: 'O serviço começa antes do fim do descanso mínimo após a escala ordinária',
      }],
    }),
    servico(5, 'Ronda de fim de semana – Rodoviária', 'Rodoviária do Plano Piloto', '2º BPM', 5, 8, 8, [
      { id: 51, funcao: 'Policial de execução', tipoFuncao: 'EXECUCAO', quantidade: 8 },
    ], { status: 'CAPTACAO_ENCERRADA' }),
  ];

  private candidaturas: CandidaturaResumo[] = [
    {
      id: 1, servicoId: 90, titulo: 'Policiamento de evento – Feira do Livro', local: 'Esplanada dos Ministérios',
      inicio: emDias(2, 9), fim: emDias(2, 17), horasContadas: 8, cotas: 1, valor: 760,
      funcao: 'Policial de execução', status: 'SELECIONADA', posicaoRanking: 3, justificativa: null, situacaoExecucao: null,
    },
    {
      id: 2, servicoId: 91, titulo: 'Reforço de policiamento – Shopping', local: 'Shopping Conjunto Nacional',
      inicio: emDias(-5, 18), fim: emDias(-5, 22), horasContadas: 4, cotas: 0.5, valor: 380,
      funcao: 'Policial de execução', status: 'NAO_SELECIONADA', posicaoRanking: 14,
      justificativa: 'Vagas preenchidas por candidatos com menos cotas no período.', situacaoExecucao: null,
    },
    {
      id: 3, servicoId: 92, titulo: 'Policiamento de evento – Corrida de rua', local: 'Eixo Monumental',
      inicio: emDias(-9, 6), fim: emDias(-9, 14), horasContadas: 8, cotas: 1, valor: 760,
      funcao: 'Policial de execução', status: 'SELECIONADA', posicaoRanking: 1, justificativa: null, situacaoExecucao: 'PRESENTE',
    },
  ];

  private proximoId = 100;

  listarServicos(): Observable<ServicoResumo[]> {
    return of(structuredClone(this.servicos)).pipe(delay(LATENCIA_MS));
  }

  obterServico(id: number): Observable<ServicoResumo> {
    const encontrado = this.servicos.find((s) => s.id === id);
    return encontrado
      ? of(structuredClone(encontrado)).pipe(delay(LATENCIA_MS))
      : throwError(() => new Error('Serviço não encontrado'));
  }

  candidatar(servicoId: number, comando: NovaCandidatura): Observable<CandidaturaResumo> {
    const alvo = this.servicos.find((s) => s.id === servicoId);
    const vaga = alvo?.vagas.find((v) => v.id === comando.vagaId);
    if (!alvo || !vaga) {
      return throwError(() => new Error('Vaga não encontrada'));
    }
    if (!comando.declaracaoArt5Aceita) {
      return throwError(() => new Error('É necessário aceitar a declaração do art. 5º da Portaria PMDF nº 1.280/2022'));
    }
    if (alvo.status !== 'CAPTACAO_ABERTA') {
      return throwError(() => new Error('A captação deste serviço não está aberta'));
    }
    if (!alvo.elegibilidade.elegivel) {
      return throwError(() => new Error(alvo.elegibilidade.violacoes.map((v) => v.mensagem).join('; ')));
    }
    if (alvo.jaCandidatou) {
      return throwError(() => new Error('Você já se candidatou a este serviço'));
    }

    alvo.jaCandidatou = true;
    const nova: CandidaturaResumo = {
      id: this.proximoId++, servicoId, titulo: alvo.titulo, local: alvo.local, inicio: alvo.inicio, fim: alvo.fim,
      horasContadas: alvo.horasContadas, cotas: alvo.cotas, valor: alvo.valor, funcao: vaga.funcao,
      status: 'CANDIDATA', posicaoRanking: null, justificativa: null, situacaoExecucao: null,
    };
    this.candidaturas = [nova, ...this.candidaturas];
    return of(structuredClone(nova)).pipe(delay(LATENCIA_MS));
  }

  minhasCandidaturas(): Observable<CandidaturaResumo[]> {
    return of(structuredClone(this.candidaturas)).pipe(delay(LATENCIA_MS));
  }

  cancelarCandidatura(id: number): Observable<void> {
    const candidatura = this.candidaturas.find((c) => c.id === id);
    if (!candidatura) {
      return throwError(() => new Error('Candidatura não encontrada'));
    }
    candidatura.status = 'CANCELADA';
    const servicoDaCandidatura = this.servicos.find((s) => s.id === candidatura.servicoId);
    if (servicoDaCandidatura) {
      servicoDaCandidatura.jaCandidatou = false;
    }
    return of(undefined).pipe(delay(LATENCIA_MS));
  }

  minhaCota(anoMes: string): Observable<CotaMensal> {
    const doMes = this.candidaturas.filter((c) => anoMesDe(c.inicio) === anoMes);
    const planejadas = doMes
      .filter((c) => c.status === 'SELECIONADA')
      .reduce((total, c) => total + c.cotas, 0);
    const realizadas = doMes
      .filter((c) => c.situacaoExecucao === 'PRESENTE')
      .reduce((total, c) => total + c.cotas, 0);
    return of({ anoMes, planejadas, realizadas, tetoIndividual: null, valorPrevisto: realizadas * 760 }).pipe(
      delay(LATENCIA_MS),
    );
  }
}
