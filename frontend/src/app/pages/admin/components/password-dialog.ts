import { Component, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { AdminService } from '@/app/core/admin.service';
import { AdminUser } from '@/app/core/api.models';
import { errorMessage, fieldErrors } from '@/app/core/api-error';
import { ConfirmService } from '@/app/core/confirm.service';
import { applyFieldErrors, controlError, maxBytes } from '@/app/core/form-errors';

@Component({
    selector: 'app-password-dialog',
    imports: [ButtonModule, DialogModule, MessageModule, PasswordModule, ReactiveFormsModule],
    template: `<p-dialog header="Definir nova senha" [modal]="true" [draggable]="false" [resizable]="false" [style]="{ width: '28rem' }" [breakpoints]="{ '640px': '95vw' }" closeAriaLabel="Fechar" [(visible)]="visible">
        <form id="password-form" class="flex flex-col gap-4" novalidate [formGroup]="form" (ngSubmit)="submit()">
            @if (failure(); as message) {
                <p-message severity="error" [text]="message" />
            }
            <p class="m-0!">Conta: {{ user()?.email }}</p>
            <div class="flex flex-col gap-2">
                <label for="admin-new-password" class="font-medium">Nova senha</label>
                <p-password inputId="admin-new-password" autocomplete="new-password" formControlName="password" appendTo="body" [feedback]="false" [toggleMask]="true" [fluid]="true" [invalid]="!!error()" />
                @if (error(); as message) {
                    <p-message severity="error" size="small" variant="simple" [text]="message" />
                } @else {
                    <small class="text-muted-color">Mínimo de 8 caracteres. Oriente a pessoa a trocá-la no perfil.</small>
                }
            </div>
        </form>
        <ng-template #footer>
            <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
            <button pButton type="submit" form="password-form" [loading]="submitting()">Definir senha</button>
        </ng-template>
    </p-dialog>`
})
export class PasswordDialog {
    private readonly admin = inject(AdminService);
    private readonly confirm = inject(ConfirmService);

    readonly visible = model(false);
    readonly user = input<AdminUser | null>(null);
    readonly saved = output<void>();

    protected readonly form = inject(NonNullableFormBuilder).group({
        password: ['', [Validators.required, Validators.minLength(8), maxBytes(72)]]
    });
    private readonly formEvents = toSignal(this.form.events);

    protected readonly submitting = signal(false);
    protected readonly failure = signal<string | null>(null);

    constructor() {
        effect(() => {
            if (this.visible()) {
                untracked(() => {
                    this.form.reset();
                    this.failure.set(null);
                    this.submitting.set(false);
                });
            }
        });
    }

    protected error(): string | null {
        this.formEvents();
        return controlError(this.form.controls.password);
    }

    protected submit(): void {
        this.form.markAllAsTouched();
        const user = this.user();
        if (this.form.invalid || this.submitting() || !user) {
            return;
        }
        this.confirm.ask(
            { header: 'Definir nova senha', message: `Trocar a senha de ${user.email}? As sessões da conta serão encerradas.`, acceptLabel: 'Definir senha' },
            () => {
                this.submitting.set(true);
                this.failure.set(null);
                this.admin.resetPassword(user.id, this.form.getRawValue().password).subscribe({
                    next: () => {
                        this.submitting.set(false);
                        this.visible.set(false);
                        this.saved.emit();
                    },
                    error: (error: unknown) => {
                        this.submitting.set(false);
                        if (!applyFieldErrors(this.form, fieldErrors(error))) {
                            this.failure.set(errorMessage(error));
                        }
                    }
                });
            }
        );
    }
}
