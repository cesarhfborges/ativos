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
import { TextareaModule } from 'primeng/textarea';
import { Asset, AssetCandidate, AssetType, WatchlistItem, WatchlistPlan } from '@/app/core/api.models';
import { errorMessage, fieldErrors, symbolIssue, SymbolIssue } from '@/app/core/api-error';
import { DecimalPaste } from '@/app/core/decimal-paste';
import { applyFieldErrors, controlError, positive } from '@/app/core/form-errors';
import { WatchlistService } from '@/app/core/watchlist.service';
import { AssetLookupDialog } from '@/app/shared/asset-lookup-dialog';

const NOTE_LIMIT = 500;

type ControlName = 'symbol' | 'name' | 'buyBelow' | 'sellAbove' | 'stopLoss' | 'note';

/** Adiciona um ativo ao acompanhamento ou altera o plano de um que já está nele. */
@Component({
    selector: 'app-watchlist-plan-dialog',
    imports: [AssetLookupDialog, ButtonModule, CurrencyPipe, DecimalPaste, DialogModule, InputNumberModule, InputTextModule, MessageModule, ReactiveFormsModule, SelectButtonModule, TextareaModule],
    template: `<p-dialog [header]="header()" [modal]="true" [draggable]="false" [resizable]="false" [style]="{ width: '32rem' }" [breakpoints]="{ '640px': '95vw' }" closeAriaLabel="Fechar" [(visible)]="visible" (onHide)="closed.emit()">
            <form id="plan-form" class="flex flex-col gap-4" novalidate [formGroup]="form" (ngSubmit)="send(false)">
                @if (failure(); as message) {
                    <p-message severity="error" [text]="message" />
                }

                @if (issue(); as found) {
                    <div class="flex flex-col gap-2">
                        <p-message severity="warn" [text]="found.detail" />
                        <div class="flex flex-wrap justify-end gap-2">
                            @if (found.suggestedType; as suggested) {
                                <p-button size="small" [label]="'Acompanhar como ' + typeLabel(suggested).toLowerCase()" [loading]="submitting()" (onClick)="useSuggestedType(suggested)" />
                            }
                            <p-button size="small" severity="secondary" [outlined]="true" label="Acompanhar mesmo assim" [loading]="submitting()" (onClick)="send(true)" />
                        </div>
                        <small class="text-surface-600 dark:text-surface-300">Acompanhado mesmo assim, o ativo fica como não verificado e pode ficar sem cotação.</small>
                    </div>
                }

                @if (!item() && !asset()) {
                    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div class="flex flex-col gap-2">
                            <label for="plan-symbol" class="font-medium">Símbolo</label>
                            <div class="flex gap-2">
                                <input pInputText id="plan-symbol" class="grow min-w-0 uppercase" maxlength="20" autocomplete="off" formControlName="symbol" [invalid]="!!error('symbol')" [attr.aria-invalid]="!!error('symbol')" />
                                <p-button icon="pi pi-search" severity="secondary" [outlined]="true" ariaLabel="Buscar ativo no mercado" (onClick)="openLookup()" />
                            </div>
                            @if (error('symbol'); as message) {
                                <p-message severity="error" size="small" variant="simple" [text]="message" />
                            }
                        </div>
                        <div class="flex flex-col gap-2">
                            <span id="plan-type-label" class="font-medium">Tipo</span>
                            <p-select-button ariaLabelledBy="plan-type-label" formControlName="assetType" optionLabel="label" optionValue="value" [options]="assetTypes" [unselectable]="true" />
                        </div>
                    </div>
                    <div class="flex flex-col gap-2">
                        <label for="plan-name" class="font-medium">Nome <span class="text-surface-600 dark:text-surface-300 font-normal">(opcional)</span></label>
                        <input pInputText id="plan-name" class="w-full" maxlength="120" autocomplete="off" formControlName="name" />
                    </div>
                }

                @if (reference(); as data) {
                    <p class="m-0 text-surface-600 dark:text-surface-300">
                        @if (data.price !== null) {
                            Cotação atual: <strong class="text-color">{{ data.price | currency: 'BRL' : 'symbol' : '1.2-8' }}</strong>.
                        }
                        @if (data.average !== null) {
                            Seu preço médio: <strong class="text-color">{{ data.average | currency: 'BRL' : 'symbol' : '1.2-8' }}</strong>.
                        }
                    </p>
                }

                <fieldset class="m-0 p-0 border-0 flex flex-col gap-4">
                    <legend class="font-medium p-0 mb-1">Seu plano <span class="text-surface-600 dark:text-surface-300 font-normal">(tudo opcional)</span></legend>
                    <div class="flex flex-col gap-2">
                        <label for="plan-buy">Comprar abaixo de</label>
                        <p-inputnumber appDecimalPaste inputId="plan-buy" formControlName="buyBelow" mode="currency" currency="BRL" locale="pt-BR" [maxFractionDigits]="8" [fluid]="true" [invalid]="!!error('buyBelow')" />
                        @if (error('buyBelow'); as message) {
                            <p-message severity="error" size="small" variant="simple" [text]="message" />
                        }
                    </div>
                    <div class="flex flex-col gap-2">
                        <label for="plan-sell">Vender acima de</label>
                        <p-inputnumber appDecimalPaste inputId="plan-sell" formControlName="sellAbove" mode="currency" currency="BRL" locale="pt-BR" [maxFractionDigits]="8" [fluid]="true" [invalid]="!!error('sellAbove')" />
                        @if (error('sellAbove'); as message) {
                            <p-message severity="error" size="small" variant="simple" [text]="message" />
                        }
                    </div>
                    <div class="flex flex-col gap-2">
                        <label for="plan-stop">Stop <span class="text-surface-600 dark:text-surface-300">(sair se cair até)</span></label>
                        <p-inputnumber appDecimalPaste inputId="plan-stop" formControlName="stopLoss" mode="currency" currency="BRL" locale="pt-BR" [maxFractionDigits]="8" [fluid]="true" [invalid]="!!error('stopLoss')" />
                        @if (error('stopLoss'); as message) {
                            <p-message severity="error" size="small" variant="simple" [text]="message" />
                        }
                    </div>
                    <div class="flex flex-col gap-2">
                        <label for="plan-note">Nota</label>
                        <textarea pTextarea id="plan-note" rows="3" class="w-full" [attr.maxlength]="noteLimit" formControlName="note" [invalid]="!!error('note')" [attr.aria-invalid]="!!error('note')"></textarea>
                        @if (error('note'); as message) {
                            <p-message severity="error" size="small" variant="simple" [text]="message" />
                        }
                    </div>
                </fieldset>
                <small class="text-surface-600 dark:text-surface-300">Os avisos de venda e stop só aparecem quando você tem o ativo na carteira.</small>
            </form>
            <ng-template #footer>
                <p-button label="Cancelar" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
                <button pButton type="submit" form="plan-form" [loading]="submitting()">{{ item() ? 'Salvar' : 'Acompanhar' }}</button>
            </ng-template>
        </p-dialog>
        <app-asset-lookup-dialog [initialQuery]="lookupQuery()" [initialType]="lookupType()" [(visible)]="lookupVisible" (chosen)="applyCandidate($event)" (closed)="restoreFocus()" />`
})
export class WatchlistPlanDialog {
    private readonly watchlist = inject(WatchlistService);

    readonly visible = model(false);
    /** Com um item, altera o plano dele. */
    readonly item = input<WatchlistItem | null>(null);
    /** Sem item, mas com um ativo já definido, como ao vir da Carteira. */
    readonly asset = input<(Asset & { price: number | null; averagePrice: number | null }) | null>(null);
    readonly saved = output<WatchlistItem>();
    readonly closed = output<void>();

    protected readonly noteLimit = NOTE_LIMIT;
    protected readonly assetTypes: { label: string; value: AssetType }[] = [
        { label: 'Ação', value: 'STOCK' },
        { label: 'Cripto', value: 'CRYPTO' }
    ];

    protected readonly form = inject(FormBuilder).group({
        symbol: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{1,20}$/)]],
        name: ['', [Validators.maxLength(120)]],
        assetType: ['STOCK' as AssetType, [Validators.required]],
        buyBelow: [null as number | null, [positive]],
        sellAbove: [null as number | null, [positive]],
        stopLoss: [null as number | null, [positive]],
        note: ['', [Validators.maxLength(NOTE_LIMIT)]]
    });
    private readonly formEvents = toSignal(this.form.events);

    protected readonly submitting = signal(false);
    protected readonly failure = signal<string | null>(null);
    protected readonly issue = signal<SymbolIssue | null>(null);
    protected readonly lookupVisible = signal(false);
    protected readonly lookupQuery = signal('');
    protected readonly lookupType = signal<AssetType>('STOCK');
    private readonly quote = signal<number | null>(null);
    private returnFocusTo: HTMLElement | null = null;

    protected readonly header = computed(() => {
        const symbol = this.item()?.symbol ?? this.asset()?.symbol;
        return symbol ? `Plano de ${symbol}` : 'Acompanhar ativo';
    });

    protected readonly reference = computed(() => {
        const item = this.item();
        const asset = this.asset();
        const price = item ? item.currentPrice : asset ? asset.price : this.quote();
        const average = item ? item.averagePrice : (asset?.averagePrice ?? null);
        return price === null && average === null ? null : { price, average };
    });

    constructor() {
        effect(() => {
            if (this.visible()) {
                const item = this.item();
                const asset = this.asset();
                untracked(() => this.reset(item, asset));
            }
        });
    }

    protected typeLabel(type: AssetType): string {
        return this.assetTypes.find((option) => option.value === type)?.label ?? '';
    }

    protected error(name: ControlName): string | null {
        this.formEvents();
        return controlError(this.form.controls[name]);
    }

    protected openLookup(): void {
        this.returnFocusTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        const { symbol, assetType } = this.form.getRawValue();
        this.lookupQuery.set(symbol?.trim() ?? '');
        this.lookupType.set(assetType ?? 'STOCK');
        this.lookupVisible.set(true);
    }

    protected restoreFocus(): void {
        this.returnFocusTo?.focus();
        this.returnFocusTo = null;
    }

    protected applyCandidate(candidate: AssetCandidate): void {
        this.form.patchValue({ symbol: candidate.symbol, name: candidate.name, assetType: candidate.assetType });
        this.quote.set(candidate.price);
        this.failure.set(null);
        this.issue.set(null);
    }

    protected useSuggestedType(type: AssetType): void {
        this.form.controls.assetType.setValue(type);
        this.send(false);
    }

    protected send(acceptUnverified: boolean): void {
        this.form.markAllAsTouched();
        if (this.form.invalid || this.submitting()) {
            return;
        }
        const { symbol, name, assetType, buyBelow, sellAbove, stopLoss, note } = this.form.getRawValue();
        const plan: WatchlistPlan = { buyBelow, sellAbove, stopLoss, note: note?.trim() || null };
        const item = this.item();
        const request = item ? this.watchlist.update(item.id, plan) : this.watchlist.add({ ...plan, symbol: symbol!.trim(), name: name?.trim() || undefined, assetType: assetType!, acceptUnverified });
        this.submitting.set(true);
        this.failure.set(null);
        this.issue.set(null);
        request.subscribe({
            next: (response) => {
                this.submitting.set(false);
                this.visible.set(false);
                this.saved.emit(response);
            },
            error: (error: unknown) => {
                this.submitting.set(false);
                const found = symbolIssue(error);
                if (found) {
                    this.issue.set(found);
                } else if (!applyFieldErrors(this.form, fieldErrors(error))) {
                    this.failure.set(errorMessage(error));
                }
            }
        });
    }

    private reset(item: WatchlistItem | null, asset: (Asset & { price: number | null }) | null): void {
        const known = item ?? asset;
        this.form.reset({
            symbol: known?.symbol ?? '',
            name: known?.name ?? '',
            assetType: known?.assetType ?? 'STOCK',
            buyBelow: item?.buyBelow ?? null,
            sellAbove: item?.sellAbove ?? null,
            stopLoss: item?.stopLoss ?? null,
            note: item?.note ?? ''
        });
        this.quote.set(null);
        this.failure.set(null);
        this.issue.set(null);
    }
}
