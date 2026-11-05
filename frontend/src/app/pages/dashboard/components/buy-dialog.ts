import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectButtonModule } from 'primeng/selectbutton';
import { AssetType, Position, Transaction } from '@/app/core/api.models';
import { errorMessage, fieldErrors } from '@/app/core/api-error';
import { applyFieldErrors, controlError, positive } from '@/app/core/form-errors';
import { averageAfterBuy, buyTotal } from '@/app/core/portfolio';
import { DecimalPaste } from '@/app/core/decimal-paste';
import { WalletService } from '@/app/core/wallet.service';

type ControlName = 'symbol' | 'name' | 'quantity' | 'unitPrice' | 'fee';

@Component({
    selector: 'app-buy-dialog',
    imports: [DecimalPaste, ButtonModule, CurrencyPipe, DialogModule, InputNumberModule, InputTextModule, MessageModule, ReactiveFormsModule, SelectButtonModule],
    template: `<p-dialog [header]="header()" [modal]="true" [draggable]="false" [resizable]="false" [style]="{ width: '32rem' }" [breakpoints]="{ '640px': '95vw' }" closeAriaLabel="Fechar" [(visible)]="visible" (onHide)="closed.emit()">
        <form id="buy-form" class="flex flex-col gap-4" novalidate [formGroup]="form" (ngSubmit)="submit()">
            @if (failure(); as message) {
                <p-message severity="error" [text]="message" />
            }

            @if (!position()) {
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div class="flex flex-col gap-2">
                        <label for="buy-symbol" class="font-medium">Símbolo</label>
                        <input
                            pInputText
                            id="buy-symbol"
                            class="w-full uppercase"
                            maxlength="20"
                            autocomplete="off"
                            formControlName="symbol"
                            [invalid]="!!error('symbol')"
                            [attr.aria-invalid]="!!error('symbol')"
                            [attr.aria-describedby]="error('symbol') ? 'buy-symbol-error' : null"
                        />
                        @if (error('symbol'); as message) {
                            <p-message id="buy-symbol-error" severity="error" size="small" variant="simple" [text]="message" />
                        }
                    </div>
                    <div class="flex flex-col gap-2">
                        <span id="buy-type-label" class="font-medium">Tipo</span>
                        <p-selectbutton ariaLabelledBy="buy-type-label" formControlName="assetType" optionLabel="label" optionValue="value" [options]="assetTypes" [unselectable]="true" />
                    </div>
                </div>
                <div class="flex flex-col gap-2">
                    <label for="buy-name" class="font-medium">Nome <span class="text-muted-color font-normal">(opcional)</span></label>
                    <input pInputText id="buy-name" class="w-full" maxlength="120" autocomplete="off" formControlName="name" [invalid]="!!error('name')" [attr.aria-invalid]="!!error('name')" />
                    @if (error('name'); as message) {
                        <p-message severity="error" size="small" variant="simple" [text]="message" />
                    }
                </div>
            }

            <div class="flex flex-col gap-2">
                <label for="buy-quantity" class="font-medium">Quantidade</label>
                <p-inputnumber appDecimalPaste inputId="buy-quantity" formControlName="quantity" locale="pt-BR" [maxFractionDigits]="8" [fluid]="true" [invalid]="!!error('quantity')" />
                @if (error('quantity'); as message) {
                    <p-message severity="error" size="small" variant="simple" [text]="message" />
                }
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div class="flex flex-col gap-2">
                    <label for="buy-price" class="font-medium">Preço unitário</label>
                    <p-inputnumber appDecimalPaste inputId="buy-price" formControlName="unitPrice" mode="currency" currency="BRL" locale="pt-BR" [maxFractionDigits]="4" [fluid]="true" [invalid]="!!error('unitPrice')" />
                    @if (error('unitPrice'); as message) {
                        <p-message severity="error" size="small" variant="simple" [text]="message" />
                    }
                </div>
                <div class="flex flex-col gap-2">
                    <label for="buy-fee" class="font-medium">Taxa <span class="text-muted-color font-normal">(opcional)</span></label>
                    <p-inputnumber appDecimalPaste inputId="buy-fee" formControlName="fee" mode="currency" currency="BRL" locale="pt-BR" [min]="0" [maxFractionDigits]="4" [fluid]="true" [invalid]="!!error('fee')" />
                    @if (error('fee'); as message) {
                        <p-message severity="error" size="small" variant="simple" [text]="message" />
                    }
                </div>
            </div>

            <dl class="grid grid-cols-2 gap-y-1 m-0 p-4 rounded-border bg-surface-100 dark:bg-surface-800">
                <dt>Total da compra</dt>
                <dd class="m-0 text-right font-semibold">{{ total() | currency: 'BRL' : 'symbol' : '1.2-4' }}</dd>
                @if (position(); as held) {
                    <dt>Preço médio atual</dt>
                    <dd class="m-0 text-right">{{ held.averagePrice | currency: 'BRL' : 'symbol' : '1.2-4' }}</dd>
                    @if (newAverage(); as average) {
                        <dt>Novo preço médio</dt>
                        <dd class="m-0 text-right font-semibold">{{ average | currency: 'BRL' : 'symbol' : '1.2-4' }}</dd>
                    }
                }
            </dl>
        </form>
        <ng-template #footer>
            <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
            <button pButton type="submit" form="buy-form" [loading]="submitting()">Comprar</button>
        </ng-template>
    </p-dialog>`
})
export class BuyDialog {
    private readonly wallet = inject(WalletService);

    readonly visible = model(false);
    /** Com uma posição, o ativo já está definido e só faltam os dados da compra. */
    readonly position = input<Position | null>(null);
    readonly completed = output<Transaction>();
    readonly closed = output<void>();

    protected readonly assetTypes: { label: string; value: AssetType }[] = [
        { label: 'Ação', value: 'STOCK' },
        { label: 'Cripto', value: 'CRYPTO' }
    ];

    protected readonly form = inject(FormBuilder).group({
        symbol: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{1,20}$/)]],
        name: ['', [Validators.maxLength(120)]],
        assetType: ['STOCK' as AssetType, [Validators.required]],
        quantity: [null as number | null, [Validators.required, positive]],
        unitPrice: [null as number | null, [Validators.required, positive]],
        fee: [null as number | null]
    });
    private readonly formEvents = toSignal(this.form.events);

    protected readonly submitting = signal(false);
    protected readonly failure = signal<string | null>(null);

    protected readonly header = computed(() => {
        const symbol = this.position()?.symbol;
        return symbol ? `Comprar ${symbol}` : 'Registrar compra';
    });

    protected readonly total = computed(() => {
        this.formEvents();
        const { quantity, unitPrice, fee } = this.form.getRawValue();
        return buyTotal(quantity ?? 0, unitPrice ?? 0, fee ?? 0);
    });

    protected readonly newAverage = computed(() => {
        this.formEvents();
        const position = this.position();
        const { quantity, unitPrice } = this.form.getRawValue();
        return position && quantity && unitPrice ? averageAfterBuy(position, quantity, unitPrice) : null;
    });

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

    protected submit(): void {
        this.form.markAllAsTouched();
        const { symbol, name, assetType, quantity, unitPrice, fee } = this.form.getRawValue();
        if (this.form.invalid || !symbol || !assetType || quantity === null || unitPrice === null || this.submitting()) {
            return;
        }
        this.submitting.set(true);
        this.failure.set(null);
        this.wallet.buy({ symbol: symbol.trim(), name: name?.trim() || undefined, assetType, quantity, unitPrice, fee: fee ?? 0 }).subscribe({
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
        this.form.reset({ symbol: position?.symbol ?? '', name: '', assetType: position?.assetType ?? 'STOCK', quantity: null, unitPrice: null, fee: null });
        this.failure.set(null);
        this.submitting.set(false);
    }
}
