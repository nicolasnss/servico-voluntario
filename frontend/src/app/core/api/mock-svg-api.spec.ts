import { firstValueFrom } from 'rxjs';

import { MockSvgApi, anoMesDe } from './mock-svg-api';

describe('MockSvgApi', () => {
  let api: MockSvgApi;

  beforeEach(() => {
    api = new MockSvgApi();
  });

  it('candidatura em serviço elegível fica aguardando seleção e marca o serviço', async () => {
    const nova = await firstValueFrom(api.candidatar(2, { vagaId: 21, declaracaoArt5Aceita: true }));

    expect(nova.status).toBe('CANDIDATA');
    expect((await firstValueFrom(api.obterServico(2))).jaCandidatou).toBe(true);
    expect((await firstValueFrom(api.minhasCandidaturas())).map((c) => c.id)).toContain(nova.id);
  });

  it('recusa candidatura sem a declaração do art. 5º', async () => {
    await expect(firstValueFrom(api.candidatar(2, { vagaId: 21, declaracaoArt5Aceita: false }))).rejects.toThrow(
      /art\. 5º/,
    );
  });

  it('recusa candidatura em serviço inelegível, explicando o motivo', async () => {
    await expect(firstValueFrom(api.candidatar(3, { vagaId: 31, declaracaoArt5Aceita: true }))).rejects.toThrow(
      /escala ordinária/,
    );
  });

  it('recusa candidatura quando a captação está encerrada', async () => {
    await expect(firstValueFrom(api.candidatar(5, { vagaId: 51, declaracaoArt5Aceita: true }))).rejects.toThrow(
      /captação/,
    );
  });

  it('não permite candidatar duas vezes, mas permite depois de cancelar', async () => {
    const nova = await firstValueFrom(api.candidatar(2, { vagaId: 21, declaracaoArt5Aceita: true }));
    await expect(firstValueFrom(api.candidatar(2, { vagaId: 21, declaracaoArt5Aceita: true }))).rejects.toThrow(
      /já se candidatou/,
    );

    await firstValueFrom(api.cancelarCandidatura(nova.id));

    await expect(
      firstValueFrom(api.candidatar(2, { vagaId: 21, declaracaoArt5Aceita: true })),
    ).resolves.toMatchObject({ status: 'CANDIDATA' });
  });

  it('separa cotas planejadas (selecionado) das realizadas (presença registrada) no mês do serviço', async () => {
    const candidaturas = await firstValueFrom(api.minhasCandidaturas());
    const executada = candidaturas.find((c) => c.situacaoExecucao === 'PRESENTE')!;

    const cota = await firstValueFrom(api.minhaCota(anoMesDe(executada.inicio)));

    expect(cota.realizadas).toBe(1);
    expect(cota.planejadas).toBeGreaterThanOrEqual(1);
    expect(cota.valorPrevisto).toBe(760);
  });

  it('não conta cotas de meses sem serviços', async () => {
    const cota = await firstValueFrom(api.minhaCota('2000-01'));

    expect(cota).toMatchObject({ planejadas: 0, realizadas: 0, valorPrevisto: 0 });
  });
});
