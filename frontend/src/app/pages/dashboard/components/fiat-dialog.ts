import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { FiatMovementType } from '@/app/core/api.models';
import { errorMessage, fieldErrors } from '@/app/core/api-error';
import { applyFieldErrors, controlError, positive } from '@/app/core/form-errors';
import { WalletService } from '@/app/core/wallet.service';

@Component({
    selector: 'app-fiat-dialog',
    imports: [ButtonModule, CurrencyPipe, DialogModule, InputNumberModule, MessageModule, ReactiveFormsModule],
    template: `<p-dialog
        [header]="isDeposit() ? 'Aportar dinheiro' : 'Resgatar dinheiro'"
        [modal]="true"
        [draggable]="false"
        [resizable]="false"
        [style]="{ width: '26rem' }"
        [breakpoints]="{ '640px': '95vw' }"
        closeAriaLabel="Fechar"
        [(visible)]="visible"
        (onHide)="closed.emit()"
    >
        <form id="fiat-form" class="flex flex-col gap-4" novalidate [formGroup]="form" (ngSubmit)="submit()">
            @if (failure(); as message) {
                <p-message severity="error" [text]="message" />
            }
            <p class="m-0!">
                Saldo em caixa: <strong>{{ balance() | currency: 'BRL' : 'symbol' : '1.2-4' }}</strong>
            </p>
            <div class="flex flex-col gap-2">
                <label for="fiat-amount" class="font-medium">Valor</label>
                <p-inputnumber inputId="fiat-amount" formControlName="amount" mode="currency" currency="BRL" locale="pt-BR" [maxFractionDigits]="4" [fluid]="true" [invalid]="!!amountError()" />
                @if (amountError(); as message) {
                    <p-message severity="error" size="small" variant="simple" [text]="message" />
                }
            </div>
        </form>
        <ng-template #footer>
            <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
            <button pButton type="submit" form="fiat-form" [loading]="submitting()">{{ isDeposit() ? 'Aportar' : 'Resgatar' }}</button>
        </ng-template>
    </p-dialog>`
})
export class FiatDialog {
    private readonly wallet = inject(WalletService);

    readonly visible = model(false);
    readonly type = input.required<FiatMovementType>();
    readonly balance = input.required<number>();
    readonly completed = output<FiatMovementType>();
    readonly closed = output<void>();

    protected readonly form = inject(FormBuilder).group({
        amount: [null as number | null, [Validators.required, positive]]
    });
    private readonly formEvents = toSignal(this.form.events);

    protected readonly submitting = signal(false);
    protected readonly failure = signal<string | null>(null);
    protected readonly isDeposit = computed(() => this.type() === 'DEPOSIT');

    protected readonly amountError = computed(() => {
        this.formEvents();
        const control = this.form.controls.amount;
        if (!this.isDeposit() && control.touched && (control.value ?? 0) > this.balance()) {
            return 'O valor é maior que o saldo em caixa.';
        }
        return controlError(control);
    });

    constructor() {
        effect(() => {
            if (this.visible()) {
                untracked(() => {
                    this.form.reset({ amount: null });
                    this.failure.set(null);
                    this.submitting.set(false);
                });
            }
        });
    }

    protected submit(): void {
        this.form.markAllAsTouched();
        const amount = this.form.controls.amount.value;
        if (this.form.invalid || amount === null || this.amountError() || this.submitting()) {
            return;
        }
        this.submitting.set(true);
        this.failure.set(null);
        const type = this.type();
        this.wallet.fiatMovement({ type, amount }).subscribe({
            next: () => {
                this.submitting.set(false);
                this.visible.set(false);
                this.completed.emit(type);
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
