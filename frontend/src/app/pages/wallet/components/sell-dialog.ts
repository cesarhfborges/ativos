import { CurrencyPipe, DecimalPipe, formatCurrency, formatNumber } from '@angular/common';
import { Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { Position, Transaction } from '@/app/core/api.models';
import { errorMessage, fieldErrors } from '@/app/core/api-error';
import { applyFieldErrors, controlError, positive } from '@/app/core/form-errors';
import { saleResult, sellNet } from '@/app/core/portfolio';
import { DecimalPaste } from '@/app/core/decimal-paste';
import { ConfirmService } from '@/app/core/confirm.service';
import { DatePickerLabels } from '@/app/core/date-picker-labels';
import { dateClause, toInstant } from '@/app/core/operation-date';
import { WalletService } from '@/app/core/wallet.service';

type ControlName = 'symbol' | 'quantity' | 'unitPrice' | 'fee' | 'executedAt';

@Component({
    selector: 'app-sell-dialog',
    imports: [DatePickerLabels, DatePickerModule, DecimalPaste, ButtonModule, CurrencyPipe, DecimalPipe, DialogModule, InputNumberModule, MessageModule, ReactiveFormsModule, SelectModule],
    template: `<p-dialog
        [header]="position() ? 'Vender ' + position()!.symbol : 'Registrar venda'"
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

            @if (!position()) {
                <div class="flex flex-col gap-2">
                    <span id="sell-symbol-label" class="font-medium">Ativo</span>
                    <p-select
                        ariaLabelledBy="sell-symbol-label"
                        formControlName="symbol"
                        optionLabel="symbol"
                        optionValue="symbol"
                        placeholder="Selecione"
                        class="w-full"
                        appendTo="body"
                        [options]="positions()"
                        [invalid]="!!error('symbol')"
                        (onChange)="onSymbolChange()"
                    />
                    @if (error('symbol'); as message) {
                        <p-message severity="error" size="small" variant="simple" [text]="message" />
                    }
                </div>
            }

            <div class="flex flex-col gap-2">
                <div class="flex items-center justify-between gap-2">
                    <label for="sell-quantity" class="font-medium">Quantidade</label>
                    <p-button label="Vender tudo" size="small" [text]="true" [disabled]="!holding()" (onClick)="sellAll()" />
                </div>
                <p-inputnumber appDecimalPaste inputId="sell-quantity" formControlName="quantity" locale="pt-BR" [maxFractionDigits]="8" [fluid]="true" [invalid]="!!error('quantity')" />
                <small class="text-muted-color">Disponível: {{ holding()?.quantity | number: '1.0-8' }}</small>
                @if (error('quantity'); as message) {
                    <p-message severity="error" size="small" variant="simple" [text]="message" />
                }
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div class="flex flex-col gap-2">
                    <label for="sell-price" class="font-medium">Preço unitário</label>
                    <p-inputnumber appDecimalPaste inputId="sell-price" formControlName="unitPrice" mode="currency" currency="BRL" locale="pt-BR" [maxFractionDigits]="8" [fluid]="true" [invalid]="!!error('unitPrice')" />
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

            <div class="flex flex-col gap-2">
                <label for="sell-date" class="font-medium">Data da operação <span class="text-muted-color font-normal">(opcional)</span></label>
                <p-datepicker
                    appDatePickerLabels
                    inputId="sell-date"
                    formControlName="executedAt"
                    dateFormat="dd/mm/yy"
                    hourFormat="24"
                    appendTo="body"
                    [showTime]="true"
                    [showIcon]="true"
                    [fluid]="true"
                    [maxDate]="today"
                    [invalid]="!!error('executedAt')"
                />
                @if (error('executedAt'); as message) {
                    <p-message severity="error" size="small" variant="simple" [text]="message" />
                }
                <small class="text-muted-color">Deixe em branco se a operação é de agora.</small>
            </div>

            <dl class="grid grid-cols-2 gap-y-1 m-0 p-4 rounded-border bg-surface-100 dark:bg-surface-800">
                <dt>Valor líquido a receber</dt>
                <dd class="m-0 text-right font-semibold">{{ net() | currency: 'BRL' : 'symbol' : '1.2-4' }}</dd>
                <dt>Preço médio da posição</dt>
                <dd class="m-0 text-right">{{ holding()?.averagePrice | currency: 'BRL' : 'symbol' : '1.2-8' }}</dd>
                @if (!backdated() && result(); as value) {
                    <dt>{{ value > 0 ? 'Lucro na venda' : 'Prejuízo na venda' }}</dt>
                    <dd class="m-0 text-right font-semibold" [class]="value > 0 ? 'text-green-700 dark:text-green-400' : 'text-red-700 dark:text-red-400'">{{ value > 0 ? '+' : '−' }} {{ absolute(value) | currency: 'BRL' : 'symbol' : '1.2-4' }}</dd>
                }
            </dl>

            @if (backdated()) {
                <small class="text-muted-color">O lucro será calculado pelo preço médio da data informada, e a quantidade será conferida naquela data.</small>
            }

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
    /** Limite do calendário: operações futuras não existem. */
    protected today = new Date();

    private readonly wallet = inject(WalletService);
    private readonly confirm = inject(ConfirmService);

    readonly visible = model(false);
    /** Com uma posição, o ativo já está definido; sem ela, o usuário escolhe entre as posições. */
    readonly position = input<Position | null>(null);
    readonly positions = input<Position[]>([]);
    readonly completed = output<Transaction>();
    readonly closed = output<void>();

    protected readonly form = inject(FormBuilder).group({
        symbol: [null as string | null, [Validators.required]],
        quantity: [null as number | null, [Validators.required, positive]],
        unitPrice: [null as number | null, [Validators.required, positive]],
        fee: [null as number | null],
        executedAt: [null as Date | null]
    });
    private readonly formEvents = toSignal(this.form.events);

    protected readonly submitting = signal(false);
    protected readonly failure = signal<string | null>(null);

    /** A posição do ativo escolhido, que limita a quantidade e dá o preço médio da prévia. */
    protected readonly holding = computed(() => {
        this.formEvents();
        const symbol = this.form.controls.symbol.value;
        const position = this.position();
        return position?.symbol === symbol ? position : (this.positions().find((candidate) => candidate.symbol === symbol) ?? null);
    });

    protected readonly backdated = computed(() => {
        this.formEvents();
        return !!this.form.controls.executedAt.value;
    });

    protected readonly net = computed(() => {
        this.formEvents();
        const { quantity, unitPrice, fee } = this.form.getRawValue();
        return sellNet(quantity ?? 0, unitPrice ?? 0, fee ?? 0);
    });

    /** Só aparece com quantidade e preço preenchidos; zero (sem resultado) também não é mostrado. */
    protected readonly result = computed(() => {
        this.formEvents();
        const position = this.holding();
        const { quantity, unitPrice, fee } = this.form.getRawValue();
        return position && quantity && unitPrice ? saleResult(position, quantity, unitPrice, fee ?? 0) : null;
    });

    protected readonly absolute = Math.abs;

    constructor() {
        // Com data informada, o limite de quantidade muda: quem confere passa a ser o backend.
        this.form.controls.executedAt.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
            this.applyCustodyLimit();
            this.form.controls.quantity.updateValueAndValidity();
        });

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
        this.form.controls.quantity.setValue(this.holding()?.quantity ?? null);
        this.form.controls.quantity.markAsTouched();
    }

    protected onSymbolChange(): void {
        this.applyCustodyLimit();
        this.form.controls.quantity.updateValueAndValidity();
    }

    protected submit(): void {
        this.form.markAllAsTouched();
        const { symbol, quantity, unitPrice, fee, executedAt } = this.form.getRawValue();
        if (this.form.invalid || !symbol || quantity === null || unitPrice === null || this.net() < 0 || this.submitting()) {
            return;
        }
        const amount = formatNumber(quantity, 'pt-BR', '1.0-8');
        const total = formatCurrency(this.net(), 'pt-BR', 'R$', 'BRL', '1.2-4');
        this.confirm.ask(
            {
                header: 'Confirmar venda',
                message: `Registrar a venda de ${amount} ${symbol}, com valor líquido de ${total}${dateClause(executedAt)}? As operações registradas não podem ser editadas nem removidas.`,
                acceptLabel: 'Registrar venda'
            },
            () => {
                this.submitting.set(true);
                this.failure.set(null);
                this.wallet.sell({ symbol, quantity, unitPrice, fee: fee ?? 0, executedAt: toInstant(executedAt) }).subscribe({
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
        );
    }

    private reset(position: Position | null): void {
        this.today = new Date();
        this.form.reset({ symbol: position?.symbol ?? null, quantity: null, unitPrice: null, fee: null, executedAt: null });
        this.applyCustodyLimit();
        this.failure.set(null);
        this.submitting.set(false);
    }

    /** Com data informada, quem confere é o backend: o limite é o que havia naquela data, não o de hoje. */
    private applyCustodyLimit(): void {
        const available = this.form.controls.executedAt.value ? Number.MAX_VALUE : (this.holding()?.quantity ?? Number.MAX_VALUE);
        this.form.controls.quantity.setValidators([Validators.required, positive, Validators.max(available)]);
    }
}
