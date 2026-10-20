import { firstValueFrom } from 'rxjs';

import { MockCaptacaoApi } from './mock-captacao-api';

describe('MockCaptacaoApi', () => {
  let api: MockCaptacaoApi;

  // Ranking esperado da vaga 11: 2005, 2001, 2002, 2003, 2006, 2004 (ids 105, 101, 102, 103, 106, 104)
  const SUGESTAO_SERVICO_1 = [105, 101, 102, 107];

  beforeEach(() => {
    api = new MockCaptacaoApi();
  });

  it('ordena por menos cotas e desempata pelo mês atual e pela antiguidade', async () => {
    const candidatos = await firstValueFrom(api.candidatos(1));

    const daVaga = candidatos.filter((c) => c.vagaId === 11);
    expect(daVaga.map((c) => c.matricula)).toEqual(['2005', '2001', '2002', '2003', '2006', '2004']);
    expect(daVaga.map((c) => c.posicao)).toEqual([1, 2, 3, 4, 5, 6]);
  });

  it('aceita a seleção que segue o ranking, sem justificativa', async () => {
    await firstValueFrom(api.selecionar(1, { candidaturaIds: SUGESTAO_SERVICO_1, justificativa: null }));

    const candidatos = await firstValueFrom(api.candidatos(1));
    expect(candidatos.filter((c) => c.status === 'SELECIONADA').map((c) => c.candidaturaId).sort()).toEqual(
      [...SUGESTAO_SERVICO_1].sort(),
    );
    expect((await firstValueFrom(api.obterServico(1))).status).toBe('SELECIONADO');
  });

  it('exige justificativa quando a seleção difere do ranking', async () => {
    const diferente = [105, 101, 104, 107];

    await expect(firstValueFrom(api.selecionar(1, { candidaturaIds: diferente, justificativa: null }))).rejects.toThrow(
      /justificativa/,
    );
    await expect(
      firstValueFrom(api.selecionar(1, { candidaturaIds: diferente, justificativa: 'curta' })),
    ).rejects.toThrow(/justificativa/);
    await expect(
      firstValueFrom(api.selecionar(1, { candidaturaIds: diferente, justificativa: 'Experiência em grandes eventos' })),
    ).resolves.toBeUndefined();
  });

  it('recusa mais selecionados do que vagas', async () => {
    const todos = [101, 102, 103, 104, 105, 106, 107];

    await expect(firstValueFrom(api.selecionar(1, { candidaturaIds: todos, justificativa: 'qualquer motivo válido' }))).rejects.toThrow(
      /no máximo 3/,
    );
  });

  it('recusa candidaturas de outro serviço', async () => {
    await expect(
      firstValueFrom(api.selecionar(1, { candidaturaIds: [...SUGESTAO_SERVICO_1, 201], justificativa: 'qualquer motivo válido' })),
    ).rejects.toThrow(/não pertencem/);
  });

  it('só permite selecionar depois de encerrada a captação', async () => {
    await expect(firstValueFrom(api.selecionar(2, { candidaturaIds: [202, 201], justificativa: null }))).rejects.toThrow(
      /encerrada/,
    );
  });

  it('abre o rascunho e encerra a captação aberta, nessa ordem', async () => {
    await firstValueFrom(api.abrirCaptacao(4));
    expect((await firstValueFrom(api.obterServico(4))).status).toBe('CAPTACAO_ABERTA');
    await expect(firstValueFrom(api.abrirCaptacao(4))).rejects.toThrow(/rascunho/);

    await firstValueFrom(api.encerrarCaptacao(2));
    expect((await firstValueFrom(api.obterServico(2))).status).toBe('CAPTACAO_ENCERRADA');
    await expect(firstValueFrom(api.encerrarCaptacao(2))).rejects.toThrow(/não está aberta/);
  });

  it('conclui o serviço quando todos os selecionados têm presença ou falta lançada', async () => {
    await firstValueFrom(api.lancarExecucao(301, 'PRESENTE'));
    expect((await firstValueFrom(api.obterServico(3))).status).toBe('SELECIONADO');

    await firstValueFrom(api.lancarExecucao(302, 'FALTOU'));
    expect((await firstValueFrom(api.obterServico(3))).status).toBe('REALIZADO');
  });

  it('não lança presença para quem não foi selecionado', async () => {
    await expect(firstValueFrom(api.lancarExecucao(303, 'PRESENTE'))).rejects.toThrow(/selecionado/);
  });
});
