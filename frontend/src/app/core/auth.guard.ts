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

/** Impede que quem já está logado veja as telas de login e registro. */
export const guestGuard: CanActivateFn = () => (inject(AuthService).isAuthenticated() ? inject(Router).createUrlTree(['/']) : true);
