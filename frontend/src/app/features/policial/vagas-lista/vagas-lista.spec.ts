import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of } from 'rxjs';

import { SvgApi } from '../../../core/api/svg-api';
import { CandidaturaResumo, CotaMensal, ServicoResumo } from '../../../core/models';
import { VagasLista } from './vagas-lista';

function servico(id: number, titulo: string, extra: Partial<ServicoResumo> = {}): ServicoResumo {
  return {
    id,
    ordemServico: null,
    titulo,
    local: 'Parque da Cidade',
    unidade: '1º BPM',
    inicio: '2030-03-10T14:00:00Z',
    fim: '2030-03-10T22:00:00Z',
    horasContadas: 8,
    cotas: 1,
    valor: 760,
    captacaoFechaEm: null,
    status: 'CAPTACAO_ABERTA',
    vagas: [{ id: id * 10, funcao: 'Execução', tipoFuncao: 'EXECUCAO', quantidade: 5 }],
    elegibilidade: { elegivel: true, violacoes: [] },
    jaCandidatou: false,
    ...extra,
  };
}

class ApiFalsa extends SvgApi {
  constructor(private readonly servicos: ServicoResumo[]) {
    super();
  }
  listarServicos(): Observable<ServicoResumo[]> {
    return of(this.servicos);
  }
  obterServico(): Observable<ServicoResumo> {
    throw new Error('não usado');
  }
  candidatar(): Observable<CandidaturaResumo> {
    throw new Error('não usado');
  }
  minhasCandidaturas(): Observable<CandidaturaResumo[]> {
    throw new Error('não usado');
  }
  cancelarCandidatura(): Observable<void> {
    throw new Error('não usado');
  }
  minhaCota(): Observable<CotaMensal> {
    throw new Error('não usado');
  }
}

describe('VagasLista', () => {
  async function renderizar(servicos: ServicoResumo[]) {
    TestBed.configureTestingModule({
      providers: [{ provide: SvgApi, useValue: new ApiFalsa(servicos) }, provideRouter([])],
    });
    const fixture = TestBed.createComponent(VagasLista);
    await fixture.whenStable();
    return fixture;
  }

  const titulos = (fixture: Awaited<ReturnType<typeof renderizar>>) =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('article h2')).map((e) =>
      e.textContent?.trim(),
    );

  const catalogo = () => [
    servico(1, 'Festival de Música'),
    servico(2, 'Policiamento escolar', {
      elegibilidade: {
        elegivel: false,
        violacoes: [{ codigo: 'CHOQUE_ESCALA_REGULAR', mensagem: 'Coincide com a escala ordinária' }],
      },
    }),
    servico(3, 'Ronda na Rodoviária', { status: 'CAPTACAO_ENCERRADA' }),
    servico(4, 'Corrida de rua', { jaCandidatou: true }),
  ];

  it('lista todos os serviços e explica por que um deles está indisponível', async () => {
    const fixture = await renderizar(catalogo());
    const html = fixture.nativeElement as HTMLElement;

    expect(titulos(fixture)).toEqual(['Festival de Música', 'Policiamento escolar', 'Ronda na Rodoviária', 'Corrida de rua']);
    expect(html.textContent).toContain('Coincide com a escala ordinária');
    expect(html.textContent).toContain('Candidatura enviada');
  });

  it('o filtro "só as que posso" esconde inelegíveis, encerradas e já candidatadas', async () => {
    const fixture = await renderizar(catalogo());
    const marcador = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[type=checkbox]')!;

    marcador.checked = true;
    marcador.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(titulos(fixture)).toEqual(['Festival de Música']);
  });

  it('a busca ignora acentos e maiúsculas', async () => {
    const fixture = await renderizar(catalogo());
    const campo = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[type=search]')!;

    campo.value = 'MUSICA';
    campo.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(titulos(fixture)).toEqual(['Festival de Música']);
  });

  it('avisa quando nenhum serviço atende aos filtros', async () => {
    const fixture = await renderizar(catalogo());
    const campo = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[type=search]')!;

    campo.value = 'inexistente';
    campo.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhuma vaga encontrada');
  });
});
