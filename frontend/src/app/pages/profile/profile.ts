import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { SkeletonModule } from 'primeng/skeleton';
import { InvestorProfile, Profile } from '@/app/core/api.models';
import { errorMessage, fieldErrors } from '@/app/core/api-error';
import { AuthService } from '@/app/core/auth.service';
import { ConfirmService } from '@/app/core/confirm.service';
import { applyFieldErrors, controlError, maxBytes, passwordsMatch, phoneFormat } from '@/app/core/form-errors';
import { fromIsoDate, toIsoDate } from '@/app/core/operation-date';
import { ProfileService } from '@/app/core/profile.service';
import { ProfileFields } from './profile-fields';

@Component({
    selector: 'app-profile',
    imports: [ButtonModule, DatePipe, InputTextModule, MessageModule, PasswordModule, ProfileFields, ReactiveFormsModule, SkeletonModule],
    template: `
        <h1 class="text-3xl! font-semibold mt-0! mb-6!">Meu perfil</h1>

        @if (loadFailure(); as message) {
            <div class="card">
                <p-message severity="error" [text]="message" styleClass="mb-4" />
                <p-button label="Tentar novamente" icon="pi pi-refresh" (onClick)="load()" />
            </div>
        } @else if (profile(); as data) {
            <div class="grid grid-cols-12 gap-4 sm:gap-8">
                <section class="col-span-12 xl:col-span-7" aria-labelledby="profile-data-title">
                    <div class="card mb-0! h-full">
                        <h2 id="profile-data-title" class="text-xl! font-semibold mt-0! mb-6!">Dados pessoais</h2>
                        <form class="flex flex-col gap-6" novalidate [formGroup]="profileForm" (ngSubmit)="saveProfile()">
                            @if (profileFailure(); as message) {
                                <p-message severity="error" [text]="message" />
                            }
                            <div class="flex flex-col gap-2">
                                <label for="profile-name" class="font-medium">Nome</label>
                                <input
                                    pInputText
                                    id="profile-name"
                                    autocomplete="name"
                                    class="w-full"
                                    formControlName="name"
                                    [invalid]="!!profileError('name')"
                                    [attr.aria-invalid]="!!profileError('name')"
                                    [attr.aria-describedby]="profileError('name') ? 'profile-name-error' : null"
                                />
                                @if (profileError('name'); as message) {
                                    <p-message id="profile-name-error" severity="error" size="small" variant="simple" [text]="message" />
                                }
                            </div>
                            <div class="flex flex-col gap-2">
                                <label for="profile-email" class="font-medium">E-mail</label>
                                <input pInputText id="profile-email" type="email" class="w-full" readonly aria-describedby="profile-email-hint" [value]="data.email" />
                                <small id="profile-email-hint" class="text-muted-color">O e-mail é usado para entrar no sistema e não pode ser alterado.</small>
                            </div>
                            <app-profile-fields idPrefix="profile" [form]="profileForm" />
                            <div class="flex flex-wrap items-center justify-between gap-4">
                                <small class="text-muted-color">Conta criada em {{ data.createdAt | date: 'longDate' }}</small>
                                <p-button type="submit" label="Salvar alterações" [loading]="savingProfile()" />
                            </div>
                        </form>
                    </div>
                </section>

                <section class="col-span-12 xl:col-span-5" aria-labelledby="profile-password-title">
                    <div class="card mb-0! h-full">
                        <h2 id="profile-password-title" class="text-xl! font-semibold mt-0! mb-2!">Alterar senha</h2>
                        <p class="text-muted-color mt-0! mb-6!">Ao alterar, as outras sessões abertas da sua conta são encerradas.</p>
                        <form class="flex flex-col gap-6" novalidate [formGroup]="passwordForm" (ngSubmit)="changePassword()">
                            @if (passwordFailure(); as message) {
                                <p-message severity="error" [text]="message" />
                            }
                            <div class="flex flex-col gap-2">
                                <label for="profile-current-password" class="font-medium">Senha atual</label>
                                <p-password inputId="profile-current-password" autocomplete="current-password" formControlName="currentPassword" [feedback]="false" [toggleMask]="true" [fluid]="true" [invalid]="!!passwordError('currentPassword')" />
                                @if (passwordError('currentPassword'); as message) {
                                    <p-message severity="error" size="small" variant="simple" [text]="message" />
                                }
                            </div>
                            <div class="flex flex-col gap-2">
                                <label for="profile-new-password" class="font-medium">Nova senha</label>
                                <p-password inputId="profile-new-password" autocomplete="new-password" formControlName="newPassword" [feedback]="false" [toggleMask]="true" [fluid]="true" [invalid]="!!passwordError('newPassword')" />
                                @if (passwordError('newPassword'); as message) {
                                    <p-message severity="error" size="small" variant="simple" [text]="message" />
                                } @else {
                                    <small class="text-muted-color">Mínimo de 8 caracteres.</small>
                                }
                            </div>
                            <div class="flex flex-col gap-2">
                                <label for="profile-confirm-password" class="font-medium">Confirmar nova senha</label>
                                <p-password inputId="profile-confirm-password" autocomplete="new-password" formControlName="confirmPassword" [feedback]="false" [toggleMask]="true" [fluid]="true" [invalid]="!!confirmError()" />
                                @if (confirmError(); as message) {
                                    <p-message severity="error" size="small" variant="simple" [text]="message" />
                                }
                            </div>
                            <div class="flex justify-end">
                                <p-button type="submit" label="Alterar senha" severity="secondary" [outlined]="true" [loading]="savingPassword()" />
                            </div>
                        </form>
                    </div>
                </section>
            </div>
        } @else {
            <p-skeleton height="24rem" />
        }
    `
})
export class ProfilePage {
    private readonly profileService = inject(ProfileService);
    private readonly auth = inject(AuthService);
    private readonly messages = inject(MessageService);
    private readonly confirm = inject(ConfirmService);

    protected readonly profileForm = inject(FormBuilder).group({
        name: ['', [Validators.required, Validators.maxLength(120)]],
        phone: ['', [phoneFormat]],
        birthDate: [null as Date | null],
        investorProfile: [null as InvestorProfile | null]
    });
    protected readonly passwordForm = inject(NonNullableFormBuilder).group(
        {
            currentPassword: ['', [Validators.required]],
            newPassword: ['', [Validators.required, Validators.minLength(8), maxBytes(72)]],
            confirmPassword: ['', [Validators.required]]
        },
        { validators: passwordsMatch('newPassword', 'confirmPassword') }
    );
    private readonly profileEvents = toSignal(this.profileForm.events);
    private readonly passwordEvents = toSignal(this.passwordForm.events);

    protected readonly profile = signal<Profile | null>(null);
    protected readonly loadFailure = signal<string | null>(null);
    protected readonly profileFailure = signal<string | null>(null);
    protected readonly passwordFailure = signal<string | null>(null);
    protected readonly savingProfile = signal(false);
    protected readonly savingPassword = signal(false);

    constructor() {
        this.load();
    }

    protected load(): void {
        this.loadFailure.set(null);
        this.profileService.get().subscribe({
            next: (profile) => this.applyProfile(profile),
            error: (error: unknown) => this.loadFailure.set(errorMessage(error))
        });
    }

    protected profileError(name: 'name'): string | null {
        this.profileEvents();
        return controlError(this.profileForm.controls[name]);
    }

    protected passwordError(name: 'currentPassword' | 'newPassword'): string | null {
        this.passwordEvents();
        return controlError(this.passwordForm.controls[name]);
    }

    protected confirmError(): string | null {
        this.passwordEvents();
        const control = this.passwordForm.controls.confirmPassword;
        return controlError(control) ?? (control.touched && this.passwordForm.hasError('passwordMismatch') ? 'As senhas não coincidem.' : null);
    }

    protected saveProfile(): void {
        this.profileForm.markAllAsTouched();
        const { name, phone, birthDate, investorProfile } = this.profileForm.getRawValue();
        if (this.profileForm.invalid || !name || this.savingProfile()) {
            return;
        }
        this.savingProfile.set(true);
        this.profileFailure.set(null);
        this.profileService.update({ name, phone: phone?.trim() || null, birthDate: toIsoDate(birthDate), investorProfile }).subscribe({
            next: (profile) => {
                this.savingProfile.set(false);
                this.applyProfile(profile);
                this.auth.updateName(profile.name);
                this.messages.add({ severity: 'success', closable: false, summary: 'Perfil atualizado' });
            },
            error: (error: unknown) => {
                this.savingProfile.set(false);
                if (!applyFieldErrors(this.profileForm, fieldErrors(error))) {
                    this.profileFailure.set(errorMessage(error));
                }
            }
        });
    }

    protected changePassword(): void {
        this.passwordForm.markAllAsTouched();
        if (this.passwordForm.invalid || this.savingPassword()) {
            return;
        }
        this.confirm.ask(
            {
                header: 'Alterar senha',
                message: 'Deseja realmente alterar sua senha? As outras sessões abertas da sua conta serão encerradas.',
                acceptLabel: 'Alterar senha'
            },
            () => {
                this.savingPassword.set(true);
                this.passwordFailure.set(null);
                this.profileService.changePassword(this.passwordForm.getRawValue()).subscribe({
                    next: () => {
                        this.savingPassword.set(false);
                        this.passwordForm.reset();
                        this.messages.add({ severity: 'success', closable: false, summary: 'Senha alterada', detail: 'As outras sessões da sua conta foram encerradas.' });
                    },
                    error: (error: unknown) => {
                        this.savingPassword.set(false);
                        if (error instanceof HttpErrorResponse && error.status === 422) {
                            this.passwordForm.controls.currentPassword.setErrors({ server: errorMessage(error) });
                        } else if (!applyFieldErrors(this.passwordForm, fieldErrors(error))) {
                            this.passwordFailure.set(errorMessage(error));
                        }
                    }
                });
            }
        );
    }

    private applyProfile(profile: Profile): void {
        this.profile.set(profile);
        this.profileForm.reset({ name: profile.name, phone: profile.phone ?? '', birthDate: fromIsoDate(profile.birthDate), investorProfile: profile.investorProfile });
    }
}
