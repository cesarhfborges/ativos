import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Bloqueia rotas privadas e guarda o destino para voltar a ele depois do login. */
export const authGuard: CanActivateFn = (_route, state) => {
    if (inject(AuthService).isAuthenticated()) {
        return true;
    }
    return inject(Router).createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } });
};

/** Esconde as telas de administração. Quem garante a permissão é o backend, que responde 403 nas chamadas. */
export const adminGuard: CanActivateFn = () => (inject(AuthService).isAdmin() ? true : inject(Router).createUrlTree(['/']));

/** Impede que quem já está logado veja as telas de login e registro. */
export const guestGuard: CanActivateFn = () => (inject(AuthService).isAuthenticated() ? inject(Router).createUrlTree(['/']) : true);
