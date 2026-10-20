import { Injectable, Signal, signal } from '@angular/core';
import { User, UserManager, WebStorageStateStore } from 'oidc-client-ts';

import { environment } from '../../../environments/environment';
import { Perfil, UsuarioLogado } from '../models';

export abstract class AuthService {
  abstract readonly usuario: Signal<UsuarioLogado | null>;

  /** Restaura a sessão existente; roda uma vez na inicialização da aplicação. */
  abstract iniciar(): Promise<void>;
  abstract entrar(retorno: string): Promise<void>;
  /** Conclui o redirecionamento do SSO e devolve a rota para a qual o usuário deve voltar. */
  abstract concluirLogin(): Promise<string>;
  abstract sair(): Promise<void>;
  abstract token(): string | null;

  possuiPerfil(...perfis: Perfil[]): boolean {
    const atuais = this.usuario()?.perfis ?? [];
    return perfis.some((perfil) => atuais.includes(perfil));
  }
}

/** Login via SSO institucional (Keycloak, realm pmdf) com Authorization Code + PKCE. */
@Injectable()
export class OidcAuthService extends AuthService {
  private readonly manager = new UserManager({
    authority: environment.oidc.authority,
    client_id: environment.oidc.clientId,
    redirect_uri: `${window.location.origin}/auth/callback`,
    post_logout_redirect_uri: window.location.origin,
    response_type: 'code',
    scope: 'openid profile',
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  });

  private readonly _usuario = signal<UsuarioLogado | null>(null);
  private accessToken: string | null = null;
  readonly usuario = this._usuario.asReadonly();

  async iniciar(): Promise<void> {
    this.aplicar(await this.manager.getUser());
  }

  async entrar(retorno: string): Promise<void> {
    await this.manager.signinRedirect({ state: retorno });
  }

  async concluirLogin(): Promise<string> {
    const user = await this.manager.signinCallback();
    this.aplicar(user ?? null);
    return typeof user?.state === 'string' ? user.state : '/';
  }

  async sair(): Promise<void> {
    await this.manager.signoutRedirect();
  }

  token(): string | null {
    return this.accessToken;
  }

  private aplicar(user: User | null): void {
    if (!user || user.expired) {
      this.accessToken = null;
      this._usuario.set(null);
      return;
    }
    this.accessToken = user.access_token;
    const profile = user.profile as Record<string, unknown>;
    this._usuario.set({
      nome: String(profile['name'] ?? profile['preferred_username'] ?? ''),
      matricula: String(profile['matricula'] ?? ''),
      perfis: perfisDoToken(user.access_token),
    });
  }
}

/** Sessão fixa de policial, para desenvolver sem o Keycloak no ar. */
@Injectable()
export class MockAuthService extends AuthService {
  private readonly _usuario = signal<UsuarioLogado | null>({
    nome: 'Sd. Teste da Silva',
    matricula: '1000001',
    perfis: ['POLICIAL'],
  });
  readonly usuario = this._usuario.asReadonly();

  async iniciar(): Promise<void> {}

  async entrar(): Promise<void> {}

  async concluirLogin(): Promise<string> {
    return '/';
  }

  async sair(): Promise<void> {
    this._usuario.set(null);
  }

  token(): string | null {
    return 'mock-token';
  }
}

const PERFIS_CONHECIDOS: readonly Perfil[] = [
  'POLICIAL',
  'RESP_CAPTACAO',
  'GESTOR_UNIDADE',
  'COORD_CENTRAL',
  'COMANDO',
  'CONTROLE',
];

/** Lê os papéis do realm do access token (claim realm_access.roles), o mesmo que o backend valida. */
function perfisDoToken(accessToken: string): Perfil[] {
  try {
    const payload = accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(payload)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    const roles: unknown = JSON.parse(json)?.realm_access?.roles;
    return Array.isArray(roles) ? PERFIS_CONHECIDOS.filter((perfil) => roles.includes(perfil)) : [];
  } catch {
    return [];
  }
}
