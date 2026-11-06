import { Routes } from '@angular/router';

export default [
    { path: '', pathMatch: 'full', title: 'Visão geral | Administração', loadComponent: () => import('./admin-overview').then((m) => m.AdminOverviewPage) },
    { path: 'usuarios', title: 'Usuários | Administração', loadComponent: () => import('./admin-users').then((m) => m.AdminUsersPage) },
    { path: 'ativos', title: 'Ativos | Administração', loadComponent: () => import('./admin-assets').then((m) => m.AdminAssetsPage) },
    { path: 'configuracoes', title: 'Configurações | Administração', loadComponent: () => import('./admin-settings').then((m) => m.AdminSettingsPage) },
    { path: 'auditoria', title: 'Auditoria | Administração', loadComponent: () => import('./admin-audit').then((m) => m.AdminAuditPage) }
] as Routes;
