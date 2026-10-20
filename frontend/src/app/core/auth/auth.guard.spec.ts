import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { authGuard, perfilGuard } from './auth.guard';
import { AuthService, MockAuthService } from './auth.service';

const rota = {} as ActivatedRouteSnapshot;
const estado = (url: string) => ({ url }) as RouterStateSnapshot;

describe('guards de autenticação', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useClass: MockAuthService }, provideRouter([])],
    });
  });

  it('authGuard libera quem tem sessão', () => {
    const resultado = TestBed.runInInjectionContext(() => authGuard(rota, estado('/vagas')));

    expect(resultado).toBe(true);
  });

  it('authGuard envia quem não tem sessão ao SSO guardando a rota pedida', async () => {
    const auth = TestBed.inject(AuthService);
    const entrar = vi.spyOn(auth, 'entrar');
    await auth.sair();

    const resultado = TestBed.runInInjectionContext(() => authGuard(rota, estado('/minha-cota')));

    expect(resultado).toBe(false);
    expect(entrar).toHaveBeenCalledWith('/minha-cota');
  });

  it('perfilGuard libera quem tem o perfil exigido', () => {
    const resultado = TestBed.runInInjectionContext(() => perfilGuard('POLICIAL')(rota, estado('/')));

    expect(resultado).toBe(true);
  });

  it('perfilGuard redireciona para a raiz quem não tem o perfil', () => {
    const resultado = TestBed.runInInjectionContext(() => perfilGuard('GESTOR_UNIDADE', 'COMANDO')(rota, estado('/gestao')));

    expect(resultado).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(AuthService).possuiPerfil('GESTOR_UNIDADE', 'COMANDO')).toBe(false);
  });
});
