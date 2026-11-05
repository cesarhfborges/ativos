import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Nestas rotas, o 401 é uma resposta esperada e quem chama decide o que fazer com ela. */
const EXPECTED_401 = ['/api/auth/login', '/api/auth/me'];

/** Envia o cookie de sessão em toda chamada e leva ao login quando a sessão expira. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
    const auth = inject(AuthService);
    const router = inject(Router);

    return next(request.clone({ withCredentials: true })).pipe(
        catchError((error: unknown) => {
            if (error instanceof HttpErrorResponse && error.status === 401 && !EXPECTED_401.includes(request.url)) {
                auth.clearSession();
                void router.navigate(['/auth/login'], { queryParams: { returnUrl: router.url } });
            }
            return throwError(() => error);
        })
    );
};
