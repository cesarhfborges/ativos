import { Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { SelectButtonModule } from 'primeng/selectbutton';
import { AdminService } from '@/app/core/admin.service';
import { AdminUser, AdminUserUpdate, InvestorProfile, Role } from '@/app/core/api.models';
import { errorMessage, fieldErrors } from '@/app/core/api-error';
import { AuthService } from '@/app/core/auth.service';
import { ConfirmService } from '@/app/core/confirm.service';
import { applyFieldErrors, controlError, maxBytes, phoneFormat } from '@/app/core/form-errors';
import { fromIsoDate, toIsoDate } from '@/app/core/operation-date';
import { ProfileFields } from '../../profile/profile-fields';

/** Sem `user`, cria uma conta; com ele, edita. */
@Component({
    selector: 'app-user-dialog',
    imports: [ButtonModule, DialogModule, InputTextModule, MessageModule, PasswordModule, ProfileFields, ReactiveFormsModule, SelectButtonModule],
    template: `<p-dialog [header]="user() ? 'Editar conta' : 'Nova conta'" [modal]="true" [draggable]="false" [resizable]="false" [style]="{ width: '36rem' }" [breakpoints]="{ '640px': '95vw' }" closeAriaLabel="Fechar" [(visible)]="visible">
        <form id="user-form" class="flex flex-col gap-6" novalidate [formGroup]="form" (ngSubmit)="submit()">
            @if (failure(); as message) {
                <p-message severity="error" [text]="message" />
            }
            <div class="flex flex-col gap-2">
                <label for="user-name" class="font-medium">Nome</label>
                <input pInputText id="user-name" class="w-full" autocomplete="off" formControlName="name" [invalid]="!!error('name')" [attr.aria-invalid]="!!error('name')" />
                @if (error('name'); as message) {
                    <p-message severity="error" size="small" variant="simple" [text]="message" />
                }
            </div>
            <div class="flex flex-col gap-2">
                <label for="user-email" class="font-medium">E-mail</label>
                <input pInputText id="user-email" type="email" class="w-full" autocomplete="off" formControlName="email" [invalid]="!!error('email')" [attr.aria-invalid]="!!error('email')" />
                @if (error('email'); as message) {
                    <p-message severity="error" size="small" variant="simple" [text]="message" />
                }
            </div>
            @if (!user()) {
                <div class="flex flex-col gap-2">
                    <label for="user-password" class="font-medium">Senha</label>
                    <p-password inputId="user-password" autocomplete="new-password" formControlName="password" appendTo="body" [feedback]="false" [toggleMask]="true" [fluid]="true" [invalid]="!!error('password')" />
                    @if (error('password'); as message) {
                        <p-message severity="error" size="small" variant="simple" [text]="message" />
                    } @else {
                        <small class="text-muted-color">Mínimo de 8 caracteres. Oriente a pessoa a trocar a senha no perfil.</small>
                    }
                </div>
            }
            <div class="flex flex-col gap-2">
                <span id="user-role-label" class="font-medium">Papel</span>
                <p-select-button ariaLabelledBy="user-role-label" formControlName="role" optionLabel="label" optionValue="value" [options]="roles" [unselectable]="true" />
            </div>
            <app-profile-fields idPrefix="user" [form]="form" />
        </form>
        <ng-template #footer>
            <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
            <button pButton type="submit" form="user-form" [loading]="submitting()">{{ user() ? 'Salvar' : 'Criar conta' }}</button>
        </ng-template>
    </p-dialog>`
})
export class UserDialog {
    private readonly admin = inject(AdminService);
    private readonly auth = inject(AuthService);
    private readonly confirm = inject(ConfirmService);

    readonly visible = model(false);
    readonly user = input<AdminUser | null>(null);
    readonly saved = output<AdminUser>();

    protected readonly roles: { label: string; value: Role }[] = [
        { label: 'Usuário', value: 'USER' },
        { label: 'Administrador', value: 'ADMIN' }
    ];

    protected readonly form = inject(FormBuilder).group({
        name: ['', [Validators.required, Validators.maxLength(120)]],
        email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
        password: ['', [Validators.required, Validators.minLength(8), maxBytes(72)]],
        role: ['USER' as Role, [Validators.required]],
        phone: ['', [phoneFormat]],
        birthDate: [null as Date | null],
        investorProfile: [null as InvestorProfile | null]
    });
    private readonly formEvents = toSignal(this.form.events);

    protected readonly submitting = signal(false);
    protected readonly failure = signal<string | null>(null);

    /** E-mail e papel ficam gravados na sessão: mudá-los exige um novo login. */
    private readonly endsSessions = computed(() => {
        this.formEvents();
        const user = this.user();
        const { email, role } = this.form.getRawValue();
        return !!user && (email?.trim().toLowerCase() !== user.email || role !== user.role);
    });

    constructor() {
        effect(() => {
            if (this.visible()) {
                const user = this.user();
                untracked(() => this.reset(user));
            }
        });
    }

    protected error(name: 'name' | 'email' | 'password'): string | null {
        this.formEvents();
        return controlError(this.form.controls[name]);
    }

    protected submit(): void {
        this.form.markAllAsTouched();
        if (this.form.invalid || this.submitting()) {
            return;
        }
        const user = this.user();
        if (!user || !this.endsSessions()) {
            this.send();
            return;
        }
        const self = user.id === this.auth.user()?.id;
        this.confirm.ask(
            {
                header: 'Alterar e-mail ou papel',
                message: self ? 'Esta é a sua conta: a alteração encerra a sua sessão e será preciso entrar novamente.' : `A alteração encerra as sessões de ${user.email}, e será preciso entrar novamente.`,
                acceptLabel: 'Salvar'
            },
            () => this.send()
        );
    }

    private send(): void {
        const { name, email, password, role, phone, birthDate, investorProfile } = this.form.getRawValue();
        const body: AdminUserUpdate = {
            name: name ?? '',
            email: email ?? '',
            role: role ?? 'USER',
            phone: phone?.trim() || null,
            birthDate: toIsoDate(birthDate),
            investorProfile
        };
        const user = this.user();
        const request = user ? this.admin.updateUser(user.id, body) : this.admin.createUser({ ...body, password: password ?? '' });

        this.submitting.set(true);
        this.failure.set(null);
        request.subscribe({
            next: (saved) => {
                this.submitting.set(false);
                this.visible.set(false);
                this.saved.emit(saved);
            },
            error: (error: unknown) => {
                this.submitting.set(false);
                if (!applyFieldErrors(this.form, fieldErrors(error))) {
                    this.failure.set(errorMessage(error));
                }
            }
        });
    }

    private reset(user: AdminUser | null): void {
        // A senha só existe ao criar: desabilitada, não bloqueia a validação da edição.
        if (user) {
            this.form.controls.password.disable();
        } else {
            this.form.controls.password.enable();
        }
        this.form.reset({
            name: user?.name ?? '',
            email: user?.email ?? '',
            password: '',
            role: user?.role ?? 'USER',
            phone: user?.phone ?? '',
            birthDate: fromIsoDate(user?.birthDate),
            investorProfile: user?.investorProfile ?? null
        });
        this.failure.set(null);
        this.submitting.set(false);
    }
}
