import { Routes } from '@angular/router';

import { authGuard, perfilGuard } from './core/auth/auth.guard';
import { Shell } from './layout/shell';

export const routes: Routes = [
  {
    path: 'auth/callback',
    loadComponent: () => import('./core/auth/auth-callback').then((m) => m.AuthCallback),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'vagas' },
      {
        path: 'vagas',
        title: 'Vagas',
        loadComponent: () =>
          import('./features/policial/vagas-lista/vagas-lista').then((m) => m.VagasLista),
      },
      {
        path: 'vagas/:id',
        title: 'Detalhe da vaga',
        loadComponent: () =>
          import('./features/policial/vaga-detalhe/vaga-detalhe').then((m) => m.VagaDetalhe),
      },
      {
        path: 'minhas-candidaturas',
        title: 'Minhas candidaturas',
        loadComponent: () =>
          import('./features/policial/minhas-candidaturas/minhas-candidaturas').then(
            (m) => m.MinhasCandidaturas,
          ),
      },
      {
        path: 'minha-cota',
        title: 'Minha cota',
        loadComponent: () =>
          import('./features/policial/minha-cota/minha-cota').then((m) => m.MinhaCota),
      },
      {
        path: 'captacao',
        canActivate: [perfilGuard('RESP_CAPTACAO', 'GESTOR_UNIDADE', 'COORD_CENTRAL')],
        children: [
          {
            path: '',
            title: 'Captação',
            loadComponent: () =>
              import('./features/captacao/captacao-lista/captacao-lista').then((m) => m.CaptacaoLista),
          },
          {
            path: ':id',
            title: 'Painel de captação',
            loadComponent: () =>
              import('./features/captacao/captacao-painel/captacao-painel').then((m) => m.CaptacaoPainel),
          },
        ],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
