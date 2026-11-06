import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, map, Observable, of, switchMap, tap } from 'rxjs';
import { LoginRequest, RegisterRequest, User } from './api.models';

const API = '/api/auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
    private readonly http = inject(HttpClient);
    private readonly currentUser = signal<User | null>(null);

    readonly user = this.currentUser.asReadonly();
    readonly isAuthenticated = computed(() => this.currentUser() !== null);

    /** Roda antes da primeira navegação: garante o cookie CSRF e recupera a sessão existente. */
    restoreSession(): Observable<void> {
        return this.fetchCsrfToken().pipe(
            switchMap(() => this.getCurrentUser()),
            catchError(() => of(null)),
            map((user) => this.currentUser.set(user))
        );
    }

    getCurrentUser(): Observable<User> {
        return this.http.get<User>(`${API}/me`);
    }

    login(body: LoginRequest): Observable<User> {
        return this.fetchCsrfToken().pipe(
            switchMap(() => this.http.post<User>(`${API}/login`, body)),
            tap((user) => this.currentUser.set(user))
        );
    }

    /** O registro não inicia a sessão: depois dele, o usuário faz o login. */
    register(body: RegisterRequest): Observable<User> {
        return this.fetchCsrfToken().pipe(switchMap(() => this.http.post<User>(`${API}/register`, body)));
    }

    logout(): Observable<void> {
        return this.http.post<void>(`${API}/logout`, null).pipe(
            tap(() => this.clearSession()),
            // O logout invalida o token CSRF; sem um novo, o próximo login seria recusado.
            switchMap(() => this.fetchCsrfToken())
        );
    }

    /** Mantém o nome da barra superior em dia depois de editar o perfil. */
    updateName(name: string): void {
        this.currentUser.update((user) => (user ? { ...user, name } : user));
    }

    /** Usado quando o backend avisa que a sessão expirou. */
    clearSession(): void {
        this.currentUser.set(null);
    }

    private fetchCsrfToken(): Observable<void> {
        return this.http.get<void>(`${API}/csrf`);
    }
}
