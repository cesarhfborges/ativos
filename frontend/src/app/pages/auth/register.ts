import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { errorMessage, fieldErrors } from '@/app/core/api-error';
import { AuthService } from '@/app/core/auth.service';
import { applyFieldErrors, controlError, maxBytes } from '@/app/core/form-errors';
import { AuthCard } from './auth-card';

@Component({
    selector: 'app-register',
    imports: [AuthCard, ButtonModule, InputTextModule, MessageModule, PasswordModule, ReactiveFormsModule, RouterLink],
    template: `
        <app-auth-card title="Criar conta" subtitle="Comece a acompanhar seus investimentos">
            <form class="flex flex-col gap-6" novalidate [formGroup]="form" (ngSubmit)="submit()">
                @if (failure(); as message) {
                    <p-message severity="error" [text]="message" />
                }

                <div class="flex flex-col gap-2">
                    <label for="register-name" class="font-medium">Nome</label>
                    <input
                        pInputText
                        id="register-name"
                        autocomplete="name"
                        class="w-full"
                        formControlName="name"
                        [invalid]="!!error('name')"
                        [attr.aria-invalid]="!!error('name')"
                        [attr.aria-describedby]="error('name') ? 'register-name-error' : null"
                    />
                    @if (error('name'); as message) {
                        <p-message id="register-name-error" severity="error" size="small" variant="simple" [text]="message" />
                    }
                </div>

                <div class="flex flex-col gap-2">
                    <label for="register-email" class="font-medium">E-mail</label>
                    <input
                        pInputText
                        id="register-email"
                        type="email"
                        autocomplete="username"
                        class="w-full"
                        formControlName="email"
                        [invalid]="!!error('email')"
                        [attr.aria-invalid]="!!error('email')"
                        [attr.aria-describedby]="error('email') ? 'register-email-error' : null"
                    />
                    @if (error('email'); as message) {
                        <p-message id="register-email-error" severity="error" size="small" variant="simple" [text]="message" />
                    }
                </div>

                <div class="flex flex-col gap-2">
                    <label for="register-password" class="font-medium">Senha</label>
                    <p-password inputId="register-password" autocomplete="new-password" formControlName="password" [feedback]="false" [toggleMask]="true" [fluid]="true" [invalid]="!!error('password')" />
                    @if (error('password'); as message) {
                        <p-message severity="error" size="small" variant="simple" [text]="message" />
                    } @else {
                        <small class="text-muted-color">Mínimo de 8 caracteres.</small>
                    }
                </div>

                <p-button type="submit" label="Criar conta" styleClass="w-full" [loading]="submitting()" />
            </form>

            <p class="text-center mt-6! mb-0!">
                Já tem conta?
                <a routerLink="/auth/login" class="text-primary font-medium underline">Entrar</a>
            </p>
        </app-auth-card>
    `
})
export class Register {
    private readonly auth = inject(AuthService);
    private readonly router = inject(Router);
    private readonly messages = inject(MessageService);

    protected readonly form = inject(NonNullableFormBuilder).group({
        name: ['', [Validators.required, Validators.maxLength(120)]],
        email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
        password: ['', [Validators.required, Validators.minLength(8), maxBytes(72)]]
    });
    private readonly formEvents = toSignal(this.form.events);

    protected readonly submitting = signal(false);
    protected readonly failure = signal<string | null>(null);

    protected error(name: 'name' | 'email' | 'password'): string | null {
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
        this.auth.register(this.form.getRawValue()).subscribe({
            next: () => {
                this.messages.add({ severity: 'success', closable: false, summary: 'Conta criada', detail: 'Agora é só entrar com seu e-mail e senha.' });
                void this.router.navigate(['/auth/login']);
            },
            error: (error: unknown) => {
                this.submitting.set(false);
                if (!applyFieldErrors(this.form, fieldErrors(error))) {
                    this.failure.set(errorMessage(error));
                }
            }
        });
    }
}
