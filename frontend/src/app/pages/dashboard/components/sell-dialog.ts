import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { Position, Transaction } from '@/app/core/api.models';
import { errorMessage, fieldErrors } from '@/app/core/api-error';
import { applyFieldErrors, controlError, positive } from '@/app/core/form-errors';
import { saleResult, sellNet } from '@/app/core/portfolio';
import { DecimalPaste } from '@/app/core/decimal-paste';
import { WalletService } from '@/app/core/wallet.service';

type ControlName = 'quantity' | 'unitPrice' | 'fee';

@Component({
    selector: 'app-sell-dialog',
    imports: [DecimalPaste, ButtonModule, CurrencyPipe, DecimalPipe, DialogModule, InputNumberModule, MessageModule, ReactiveFormsModule],
    template: `<p-dialog
        [header]="'Vender ' + (position()?.symbol ?? '')"
        [modal]="true"
        [draggable]="false"
        [resizable]="false"
        [style]="{ width: '32rem' }"
        [breakpoints]="{ '640px': '95vw' }"
        closeAriaLabel="Fechar"
        [(visible)]="visible"
        (onHide)="closed.emit()"
    >
        <form id="sell-form" class="flex flex-col gap-4" novalidate [formGroup]="form" (ngSubmit)="submit()">
            @if (failure(); as message) {
                <p-message severity="error" [text]="message" />
            }

            <div class="flex flex-col gap-2">
                <div class="flex items-center justify-between gap-2">
                    <label for="sell-quantity" class="font-medium">Quantidade</label>
                    <p-button label="Vender tudo" size="small" [text]="true" (onClick)="sellAll()" />
                </div>
                <p-inputnumber appDecimalPaste inputId="sell-quantity" formControlName="quantity" locale="pt-BR" [maxFractionDigits]="8" [fluid]="true" [invalid]="!!error('quantity')" />
                <small class="text-muted-color">Disponível: {{ position()?.quantity | number: '1.0-8' }}</small>
                @if (error('quantity'); as message) {
                    <p-message severity="error" size="small" variant="simple" [text]="message" />
                }
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div class="flex flex-col gap-2">
                    <label for="sell-price" class="font-medium">Preço unitário</label>
                    <p-inputnumber appDecimalPaste inputId="sell-price" formControlName="unitPrice" mode="currency" currency="BRL" locale="pt-BR" [maxFractionDigits]="4" [fluid]="true" [invalid]="!!error('unitPrice')" />
                    @if (error('unitPrice'); as message) {
                        <p-message severity="error" size="small" variant="simple" [text]="message" />
                    }
                </div>
                <div class="flex flex-col gap-2">
                    <label for="sell-fee" class="font-medium">Taxa <span class="text-muted-color font-normal">(opcional)</span></label>
                    <p-inputnumber appDecimalPaste inputId="sell-fee" formControlName="fee" mode="currency" currency="BRL" locale="pt-BR" [min]="0" [maxFractionDigits]="4" [fluid]="true" [invalid]="!!error('fee')" />
                    @if (error('fee'); as message) {
                        <p-message severity="error" size="small" variant="simple" [text]="message" />
                    }
                </div>
            </div>

            <dl class="grid grid-cols-2 gap-y-1 m-0 p-4 rounded-border bg-surface-100 dark:bg-surface-800">
                <dt>Valor líquido a receber</dt>
                <dd class="m-0 text-right font-semibold">{{ net() | currency: 'BRL' : 'symbol' : '1.2-4' }}</dd>
                <dt>Preço médio da posição</dt>
                <dd class="m-0 text-right">{{ position()?.averagePrice | currency: 'BRL' : 'symbol' : '1.2-4' }}</dd>
                @if (result(); as value) {
                    <dt>{{ value > 0 ? 'Lucro na venda' : 'Prejuízo na venda' }}</dt>
                    <dd class="m-0 text-right font-semibold" [class]="value > 0 ? 'text-green-700 dark:text-green-400' : 'text-red-700 dark:text-red-400'">{{ value > 0 ? '+' : '−' }} {{ absolute(value) | currency: 'BRL' : 'symbol' : '1.2-4' }}</dd>
                }
            </dl>

            @if (net() < 0) {
                <p-message severity="error" size="small">A taxa não pode ser maior que o valor da venda.</p-message>
            }
        </form>
        <ng-template #footer>
            <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
            <button pButton type="submit" form="sell-form" [loading]="submitting()">Vender</button>
        </ng-template>
    </p-dialog>`
})
export class SellDialog {
    private readonly wallet = inject(WalletService);

    readonly visible = model(false);
    readonly position = input<Position | null>(null);
    readonly completed = output<Transaction>();
    readonly closed = output<void>();

    protected readonly form = inject(FormBuilder).group({
        quantity: [null as number | null, [Validators.required, positive]],
        unitPrice: [null as number | null, [Validators.required, positive]],
        fee: [null as number | null]
    });
    private readonly formEvents = toSignal(this.form.events);

    protected readonly submitting = signal(false);
    protected readonly failure = signal<string | null>(null);

    protected readonly net = computed(() => {
        this.formEvents();
        const { quantity, unitPrice, fee } = this.form.getRawValue();
        return sellNet(quantity ?? 0, unitPrice ?? 0, fee ?? 0);
    });

    /** Só aparece com quantidade e preço preenchidos; zero (sem resultado) também não é mostrado. */
    protected readonly result = computed(() => {
        this.formEvents();
        const position = this.position();
        const { quantity, unitPrice, fee } = this.form.getRawValue();
        return position && quantity && unitPrice ? saleResult(position, quantity, unitPrice, fee ?? 0) : null;
    });

    protected readonly absolute = Math.abs;

    constructor() {
        effect(() => {
            if (this.visible()) {
                const position = this.position();
                untracked(() => this.reset(position));
            }
        });
    }

    protected error(name: ControlName): string | null {
        this.formEvents();
        return controlError(this.form.controls[name]);
    }

    protected sellAll(): void {
        this.form.controls.quantity.setValue(this.position()?.quantity ?? null);
        this.form.controls.quantity.markAsTouched();
    }

    protected submit(): void {
        this.form.markAllAsTouched();
        const symbol = this.position()?.symbol;
        const { quantity, unitPrice, fee } = this.form.getRawValue();
        if (this.form.invalid || !symbol || quantity === null || unitPrice === null || this.net() < 0 || this.submitting()) {
            return;
        }
        this.submitting.set(true);
        this.failure.set(null);
        this.wallet.sell({ symbol, quantity, unitPrice, fee: fee ?? 0 }).subscribe({
            next: (response) => {
                this.submitting.set(false);
                this.visible.set(false);
                this.completed.emit(response);
            },
            error: (error: unknown) => {
                this.submitting.set(false);
                if (!applyFieldErrors(this.form, fieldErrors(error))) {
                    this.failure.set(errorMessage(error));
                }
            }
        });
    }

    private reset(position: Position | null): void {
        const quantity = this.form.controls.quantity;
        quantity.setValidators([Validators.required, positive, Validators.max(position?.quantity ?? 0)]);
        this.form.reset({ quantity: null, unitPrice: null, fee: null });
        this.failure.set(null);
        this.submitting.set(false);
    }
}
