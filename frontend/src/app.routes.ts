import { Routes } from '@angular/router';
import { authGuard } from './app/core/auth.guard';
import { AppLayout } from './app/layout/component/app.layout';
import { Notfound } from './app/pages/notfound/notfound';

export const appRoutes: Routes = [
    {
        path: '',
        component: AppLayout,
        canActivate: [authGuard],
        children: [
            { path: '', title: 'Painel | Ativos', loadComponent: () => import('./app/pages/dashboard/dashboard').then((m) => m.Dashboard) },
            { path: 'extrato', title: 'Extrato | Ativos', loadComponent: () => import('./app/pages/statement/statement').then((m) => m.Statement) }
        ]
    },
    { path: 'notfound', title: 'Página não encontrada | Ativos', component: Notfound },
    { path: 'auth', loadChildren: () => import('./app/pages/auth/auth.routes') },
    { path: '**', redirectTo: '/notfound' }
];
