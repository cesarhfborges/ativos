import { Routes } from '@angular/router';
import { adminGuard, authGuard } from './app/core/auth.guard';
import { AppLayout } from './app/layout/component/app.layout';
import { Notfound } from './app/pages/notfound/notfound';

export const appRoutes: Routes = [
    {
        path: '',
        component: AppLayout,
        canActivate: [authGuard],
        children: [
            { path: '', title: 'Painel | Ativos', loadComponent: () => import('./app/pages/dashboard/dashboard').then((m) => m.Dashboard) },
            { path: 'carteira', title: 'Carteira | Ativos', loadComponent: () => import('./app/pages/wallet/wallet').then((m) => m.Wallet) },
            { path: 'acompanhamento', title: 'Acompanhamento | Ativos', loadComponent: () => import('./app/pages/watchlist/watchlist').then((m) => m.Watchlist) },
            { path: 'extrato', title: 'Extrato | Ativos', loadComponent: () => import('./app/pages/statement/statement').then((m) => m.Statement) },
            { path: 'projecoes', title: 'Projeções | Ativos', loadComponent: () => import('./app/pages/projections/projections').then((m) => m.Projections) },
            { path: 'perfil', title: 'Meu perfil | Ativos', loadComponent: () => import('./app/pages/profile/profile').then((m) => m.ProfilePage) },
            { path: 'admin', canActivate: [adminGuard], loadChildren: () => import('./app/pages/admin/admin.routes') }
        ]
    },
    { path: 'notfound', title: 'Página não encontrada | Ativos', component: Notfound },
    { path: 'auth', loadChildren: () => import('./app/pages/auth/auth.routes') },
    { path: '**', redirectTo: '/notfound' }
];
