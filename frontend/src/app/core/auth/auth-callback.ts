import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from './auth.service';

@Component({
  selector: 'app-auth-callback',
  template: `
    @if (erro()) {
      <div class="estado">
        <p>Não foi possível concluir o login.</p>
        <button type="button" class="btn" (click)="tentarNovamente()">Tentar novamente</button>
      </div>
    } @else {
      <p class="estado">Concluindo o login…</p>
    }
  `,
})
export class AuthCallback {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly erro = signal(false);

  constructor() {
    this.auth
      .concluirLogin()
      .then((retorno) => this.router.navigateByUrl(retorno, { replaceUrl: true }))
      .catch(() => this.erro.set(true));
  }

  protected tentarNovamente(): void {
    void this.auth.entrar('/');
  }
}
