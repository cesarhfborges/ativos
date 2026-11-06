import { CurrencyPipe, formatCurrency, formatNumber } from '@angular/common';
import { Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, of, Subject, switchMap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectButtonModule } from 'primeng/selectbutton';
import { Asset, AssetType, Position, Transaction } from '@/app/core/api.models';
import { errorMessage, fieldErrors } from '@/app/core/api-error';
import { applyFieldErrors, controlError, positive } from '@/app/core/form-errors';
import { averageAfterBuy, buyTotal } from '@/app/core/portfolio';
import { DecimalPaste } from '@/app/core/decimal-paste';
import { ConfirmService } from '@/app/core/confirm.service';
import { DatePickerLabels } from '@/app/core/date-picker-labels';
import { dateClause, toInstant } from '@/app/core/operation-date';
import { WalletService } from '@/app/core/wallet.service';

type ControlName = 'symbol' | 'name' | 'quantity' | 'unitPrice' | 'fee' | 'executedAt';

@Component({
    selector: 'app-buy-dialog',
    imports: [DatePickerLabels, AutoCompleteModule, DatePickerModule, DecimalPaste, ButtonModule, CurrencyPipe, DialogModule, InputNumberModule, InputTextModule, MessageModule, ReactiveFormsModule, SelectButtonModule],
    template: `<p-dialog [header]="header()" [modal]="true" [draggable]="false" [resizable]="false" [style]="{ width: '32rem' }" [breakpoints]="{ '640px': '95vw' }" closeAriaLabel="Fechar" [(visible)]="visible" (onHide)="closed.emit()">
        <form id="buy-form" class="flex flex-col gap-4" novalidate [formGroup]="form" (ngSubmit)="submit()">
            @if (failure(); as message) {
                <p-message severity="error" [text]="message" />
            }

            @if (!position()) {
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div class="flex flex-col gap-2">
                        <label for="buy-symbol" class="font-medium">Símbolo</label>
                        <p-auto-complete
                            inputId="buy-symbol"
                            inputStyleClass="w-full uppercase"
                            formControlName="symbol"
                            appendTo="body"
                            [maxlength]="20"
                            [fluid]="true"
                            [delay]="200"
                            [showEmptyMessage]="false"
                            [suggestions]="symbols()"
                            [invalid]="!!error('symbol')"
                            (completeMethod)="searches.next($event.query)"
                            (onSelect)="onSymbolSelected($event.value)"
                        >
                            <ng-template #item let-symbol>
                                <div class="flex flex-col">
                                    <span class="font-medium">{{ symbol }}</span>
                                    @if (assetOf(symbol); as asset) {
                                        <span class="text-sm text-muted-color" aria-hidden="true">{{ asset.name }} · {{ typeLabel(asset.assetType) }}</span>
                                    }
                                </div>
                            </ng-template>
                        </p-auto-complete>
                        @if (error('symbol'); as message) {
                            <p-message severity="error" size="small" variant="simple" [text]="message" />
                        }
                    </div>
                    <div class="flex flex-col gap-2">
                        <span id="buy-type-label" class="font-medium">Tipo</span>
                        <p-select-button ariaLabelledBy="buy-type-label" formControlName="assetType" optionLabel="label" optionValue="value" [options]="assetTypes" [unselectable]="true" />
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

            <div class="flex flex-col gap-2">
                <label for="buy-date" class="font-medium">Data da operação <span class="text-muted-color font-normal">(opcional)</span></label>
                <p-datepicker
                    appDatePickerLabels
                    inputId="buy-date"
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
                <dt>Total da compra</dt>
                <dd class="m-0 text-right font-semibold">{{ total() | currency: 'BRL' : 'symbol' : '1.2-4' }}</dd>
                @if (position(); as held) {
                    <dt>Preço médio atual</dt>
                    <dd class="m-0 text-right">{{ held.averagePrice | currency: 'BRL' : 'symbol' : '1.2-4' }}</dd>
                    @if (!backdated() && newAverage(); as average) {
                        <dt>Novo preço médio</dt>
                        <dd class="m-0 text-right font-semibold">{{ average | currency: 'BRL' : 'symbol' : '1.2-4' }}</dd>
                    }
                }
            </dl>

            @if (backdated()) {
                <small class="text-muted-color">O novo preço médio será calculado considerando a data informada.</small>
            }
        </form>
        <ng-template #footer>
            <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
            <button pButton type="submit" form="buy-form" [loading]="submitting()">Comprar</button>
        </ng-template>
    </p-dialog>`
})
export class BuyDialog {
    /** Limite do calendário: operações futuras não existem. */
    protected today = new Date();

    private readonly wallet = inject(WalletService);
    private readonly confirm = inject(ConfirmService);

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
        fee: [null as number | null],
        executedAt: [null as Date | null]
    });
    private readonly formEvents = toSignal(this.form.events);

    protected readonly searches = new Subject<string>();
    private readonly suggestions = signal<Asset[]>([]);
    /** Só os símbolos: assim o campo vale sempre um texto, seja digitado ou escolhido na lista. */
    protected readonly symbols = computed(() => this.suggestions().map((asset) => asset.symbol));
    /** Ativo já cadastrado: seu tipo e nome valem para todos, então não podem ser alterados aqui. */
    protected readonly knownAsset = signal<Asset | null>(null);

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

    protected readonly backdated = computed(() => {
        this.formEvents();
        return !!this.form.controls.executedAt.value;
    });

    protected readonly newAverage = computed(() => {
        this.formEvents();
        const position = this.position();
        const { quantity, unitPrice } = this.form.getRawValue();
        return position && quantity && unitPrice ? averageAfterBuy(position, quantity, unitPrice) : null;
    });

    constructor() {
        this.searches
            .pipe(
                switchMap((query) => this.wallet.searchAssets(query).pipe(catchError(() => of<Asset[]>([])))),
                takeUntilDestroyed()
            )
            .subscribe((assets) => {
                this.suggestions.set(assets);
                // Quem digita o símbolo inteiro sem clicar na sugestão também tem o tipo preenchido.
                const typed = this.form.controls.symbol.value?.trim().toUpperCase();
                const match = assets.find((asset) => asset.symbol === typed);
                if (match) {
                    this.applyAsset(match);
                }
            });

        // Ao mudar o símbolo depois de escolher um ativo, tipo e nome voltam a ser editáveis.
        this.form.controls.symbol.valueChanges.pipe(takeUntilDestroyed()).subscribe((symbol) => {
            if (this.knownAsset() && this.knownAsset()?.symbol !== symbol?.trim().toUpperCase()) {
                this.releaseAsset();
            }
        });

        effect(() => {
            if (this.visible()) {
                const position = this.position();
                untracked(() => this.reset(position));
            }
        });
    }

    protected onSymbolSelected(symbol: string): void {
        const asset = this.assetOf(symbol);
        if (asset) {
            this.applyAsset(asset);
        }
    }

    protected assetOf(symbol: string): Asset | undefined {
        return this.suggestions().find((asset) => asset.symbol === symbol);
    }

    private applyAsset(asset: Asset): void {
        this.knownAsset.set(asset);
        this.form.controls.assetType.setValue(asset.assetType);
        this.form.controls.assetType.disable();
        this.form.controls.name.setValue(asset.name);
        this.form.controls.name.disable();
    }

    protected typeLabel(type: AssetType): string {
        return this.assetTypes.find((option) => option.value === type)?.label ?? '';
    }

    protected error(name: ControlName): string | null {
        this.formEvents();
        return controlError(this.form.controls[name]);
    }

    protected submit(): void {
        this.form.markAllAsTouched();
        const { symbol, name, assetType, quantity, unitPrice, fee, executedAt } = this.form.getRawValue();
        if (this.form.invalid || !symbol || !assetType || quantity === null || unitPrice === null || this.submitting()) {
            return;
        }
        const amount = formatNumber(quantity, 'pt-BR', '1.0-8');
        const total = formatCurrency(this.total(), 'pt-BR', 'R$', 'BRL', '1.2-4');
        this.confirm.ask(
            {
                header: 'Confirmar compra',
                message: `Registrar a compra de ${amount} ${symbol.trim().toUpperCase()}, no total de ${total}${dateClause(executedAt)}? As operações registradas não podem ser editadas nem removidas.`,
                acceptLabel: 'Registrar compra'
            },
            () => {
                this.submitting.set(true);
                this.failure.set(null);
                this.wallet.buy({ symbol: symbol.trim(), name: name?.trim() || undefined, assetType, quantity, unitPrice, fee: fee ?? 0, executedAt: toInstant(executedAt) }).subscribe({
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

    private releaseAsset(): void {
        this.knownAsset.set(null);
        this.form.controls.assetType.enable();
        this.form.controls.name.enable();
        this.form.controls.name.setValue('');
    }

    private reset(position: Position | null): void {
        this.releaseAsset();
        this.suggestions.set([]);
        this.today = new Date();
        this.form.reset({ symbol: position?.symbol ?? '', name: '', assetType: position?.assetType ?? 'STOCK', quantity: null, unitPrice: null, fee: null, executedAt: null });
        this.failure.set(null);
        this.submitting.set(false);
    }
}
