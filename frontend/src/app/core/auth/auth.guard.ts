import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Perfil } from '../models';
import { AuthService } from './auth.service';

/** Sem sessão, manda para o SSO guardando a rota pedida para voltar depois. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.usuario()) {
    return true;
  }
  void auth.entrar(state.url);
  return false;
};

export function perfilGuard(...perfis: Perfil[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    return auth.possuiPerfil(...perfis) || inject(Router).createUrlTree(['/']);
  };
}
