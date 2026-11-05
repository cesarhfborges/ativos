import { Routes } from '@angular/router';
import { guestGuard } from '@/app/core/auth.guard';

export default [
    { path: 'login', title: 'Entrar | Ativos', canActivate: [guestGuard], loadComponent: () => import('./login').then((m) => m.Login) },
    { path: 'register', title: 'Criar conta | Ativos', canActivate: [guestGuard], loadComponent: () => import('./register').then((m) => m.Register) },
    { path: '', pathMatch: 'full', redirectTo: 'login' }
] as Routes;
