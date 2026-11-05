import { Component, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { errorMessage } from '@/app/core/api-error';
import { AuthService } from '@/app/core/auth.service';
import { controlError } from '@/app/core/form-errors';
import { AuthCard } from './auth-card';

@Component({
    selector: 'app-login',
    imports: [AuthCard, ButtonModule, InputTextModule, MessageModule, PasswordModule, ReactiveFormsModule, RouterLink],
    template: `
        <app-auth-card title="Entrar" subtitle="Acesse sua carteira de investimentos">
            <form class="flex flex-col gap-6" novalidate [formGroup]="form" (ngSubmit)="submit()">
                @if (failure(); as message) {
                    <p-message severity="error" [text]="message" />
                }

                <div class="flex flex-col gap-2">
                    <label for="login-email" class="font-medium">E-mail</label>
                    <input
                        pInputText
                        id="login-email"
                        type="email"
                        autocomplete="username"
                        class="w-full"
                        formControlName="email"
                        [invalid]="!!error('email')"
                        [attr.aria-invalid]="!!error('email')"
                        [attr.aria-describedby]="error('email') ? 'login-email-error' : null"
                    />
                    @if (error('email'); as message) {
                        <p-message id="login-email-error" severity="error" size="small" variant="simple" [text]="message" />
                    }
                </div>

                <div class="flex flex-col gap-2">
                    <label for="login-password" class="font-medium">Senha</label>
                    <p-password inputId="login-password" autocomplete="current-password" formControlName="password" [feedback]="false" [toggleMask]="true" [fluid]="true" [invalid]="!!error('password')" />
                    @if (error('password'); as message) {
                        <p-message severity="error" size="small" variant="simple" [text]="message" />
                    }
                </div>

                <p-button type="submit" label="Entrar" styleClass="w-full" [loading]="submitting()" />
            </form>

            <p class="text-center mt-6! mb-0!">
                Ainda não tem conta?
                <a routerLink="/auth/register" class="text-primary font-medium underline">Criar conta</a>
            </p>
        </app-auth-card>
    `
})
export class Login {
    private readonly auth = inject(AuthService);
    private readonly router = inject(Router);

    /** Destino original, vindo do guard pela query string. */
    readonly returnUrl = input<string>();

    protected readonly form = inject(NonNullableFormBuilder).group({
        email: ['', [Validators.required, Validators.maxLength(254)]],
        password: ['', [Validators.required, Validators.maxLength(72)]]
    });
    private readonly formEvents = toSignal(this.form.events);

    protected readonly submitting = signal(false);
    protected readonly failure = signal<string | null>(null);

    protected error(name: 'email' | 'password'): string | null {
        this.formEvents();
        return controlError(this.form.controls[name]);
    }

    protected submit(): void {
        this.form.markAllAsTouched();
        if (this.form.invalid || this.submitting()) {
            return;
        }
        this.submitting.set(true);
        this.failure.set(null);
        this.auth.login(this.form.getRawValue()).subscribe({
            next: () => void this.router.navigateByUrl(this.returnUrl() ?? '/'),
            error: (error: unknown) => {
                this.submitting.set(false);
                this.failure.set(errorMessage(error));
            }
        });
    }
}
