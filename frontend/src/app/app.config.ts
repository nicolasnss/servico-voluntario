import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localePt from '@angular/common/locales/pt';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { CaptacaoApi } from './core/api/captacao-api';
import { HttpCaptacaoApi } from './core/api/http-captacao-api';
import { HttpSvgApi } from './core/api/http-svg-api';
import { MockCaptacaoApi } from './core/api/mock-captacao-api';
import { MockSvgApi } from './core/api/mock-svg-api';
import { SvgApi } from './core/api/svg-api';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService, MockAuthService, OidcAuthService } from './core/auth/auth.service';

registerLocaleData(localePt);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    { provide: AuthService, useClass: environment.useMocks ? MockAuthService : OidcAuthService },
    { provide: SvgApi, useClass: environment.useMocks ? MockSvgApi : HttpSvgApi },
    { provide: CaptacaoApi, useClass: environment.useMocks ? MockCaptacaoApi : HttpCaptacaoApi },
    provideAppInitializer(() => inject(AuthService).iniciar()),
  ],
};
