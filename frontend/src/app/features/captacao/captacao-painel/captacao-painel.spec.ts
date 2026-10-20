import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { CaptacaoApi } from '../../../core/api/captacao-api';
import { MockCaptacaoApi } from '../../../core/api/mock-captacao-api';
import { CaptacaoPainel } from './captacao-painel';

describe('CaptacaoPainel', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [{ provide: CaptacaoApi, useClass: MockCaptacaoApi }, provideRouter([])],
    });
  });

  afterEach(() => vi.useRealTimers());

  async function abrir(id: string) {
    const fixture = TestBed.createComponent(CaptacaoPainel);
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(600);
    await fixture.whenStable();
    return fixture;
  }

  const atualizar = async (f: Awaited<ReturnType<typeof abrir>>) => {
    await vi.advanceTimersByTimeAsync(50);
    f.detectChanges();
  };
  const raiz = (f: { nativeElement: unknown }) => f.nativeElement as HTMLElement;
  const caixa = (f: { nativeElement: unknown }, nome: string) =>
    raiz(f).querySelector<HTMLInputElement>(`input[aria-label="Selecionar ${nome}"]`)!;
  const marcar = async (f: Awaited<ReturnType<typeof abrir>>, nome: string, valor: boolean) => {
    const c = caixa(f, nome);
    c.checked = valor;
    c.dispatchEvent(new Event('change'));
    await atualizar(f);
  };
  const botaoConfirmar = (f: Awaited<ReturnType<typeof abrir>>) =>
    Array.from(raiz(f).querySelectorAll('button')).find((b) => b.textContent?.includes('Confirmar seleção'))!;

  it('já marca a sugestão do ranking e libera a confirmação sem justificativa', async () => {
    const fixture = await abrir('1');

    const marcados = Array.from(raiz(fixture).querySelectorAll<HTMLInputElement>('input[type=checkbox]'))
      .filter((c) => c.checked)
      .map((c) => c.getAttribute('aria-label'));

    expect(marcados).toEqual([
      'Selecionar Sd. Esteves',
      'Selecionar Sd. Alves',
      'Selecionar Cb. Barros',
      'Selecionar 3º Sgt. Gomes',
    ]);
    expect(raiz(fixture).querySelector('textarea')).toBeNull();
    expect(botaoConfirmar(fixture).disabled).toBe(false);
  });

  it('exige justificativa quando a escolha foge do ranking', async () => {
    const fixture = await abrir('1');

    await marcar(fixture, 'Cb. Barros', false);
    await marcar(fixture, 'Sgt. Dias', true);

    const texto = raiz(fixture).querySelector<HTMLTextAreaElement>('textarea')!;
    expect(texto).not.toBeNull();
    expect(botaoConfirmar(fixture).disabled).toBe(true);

    texto.value = 'Experiência em grandes eventos';
    texto.dispatchEvent(new Event('input'));
    await atualizar(fixture);

    expect(botaoConfirmar(fixture).disabled).toBe(false);
  });

  it('bloqueia a confirmação quando há mais marcados do que vagas', async () => {
    const fixture = await abrir('1');

    await marcar(fixture, 'Sd. Cunha', true);

    expect(raiz(fixture).textContent).toContain('mais policiais marcados do que vagas');
    expect(botaoConfirmar(fixture).disabled).toBe(true);
  });

  it('confirmar a seleção leva o serviço para o lançamento de presenças', async () => {
    const fixture = await abrir('1');

    botaoConfirmar(fixture).click();
    await vi.advanceTimersByTimeAsync(800);
    await fixture.whenStable();

    expect(raiz(fixture).textContent).toContain('Seleção concluída');
    expect(raiz(fixture).textContent).toContain('Registre se o policial prestou o serviço');
  });

  it('oferece abrir a captação de um rascunho', async () => {
    const fixture = await abrir('4');

    expect(raiz(fixture).textContent).toContain('Abrir captação');
  });
});
