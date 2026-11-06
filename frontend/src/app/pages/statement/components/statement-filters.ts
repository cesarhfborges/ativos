import { Component, computed, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { AssetType, TransactionStatus, TransactionType } from '@/app/core/api.models';
import { DecimalPaste } from '@/app/core/decimal-paste';
import { EMPTY_STATEMENT_FILTER, hasActiveFilter, isValidFilter, StatementFilter } from '@/app/core/statement-filter';
import { TRANSACTION_DISPLAY } from '@/app/core/transaction-display';

@Component({
    selector: 'app-statement-filters',
    imports: [ButtonModule, DatePickerModule, DecimalPaste, FormsModule, InputNumberModule, InputTextModule, MessageModule, SelectModule],
    template: `<form class="card mb-4! grid grid-cols-12 gap-4 items-start" role="search" aria-label="Filtros do extrato" (submit)="$event.preventDefault()">
        <div class="col-span-12 md:col-span-6 xl:col-span-3 flex flex-col gap-2">
            <label for="statement-asset" class="font-medium">Ativo</label>
            <input pInputText id="statement-asset" name="asset" type="search" class="w-full" maxlength="120" autocomplete="off" placeholder="Símbolo ou nome" [ngModel]="filter().asset" (ngModelChange)="change('asset', $event)" />
        </div>
        <div class="col-span-6 md:col-span-3 xl:col-span-3 flex flex-col gap-2">
            <span id="statement-type-label" class="font-medium">Tipo</span>
            <p-select name="type" ariaLabelledBy="statement-type-label" optionLabel="label" optionValue="value" [fluid]="true" [options]="typeOptions" [ngModel]="filter().type" (ngModelChange)="change('type', $event)" />
        </div>
        <div class="col-span-6 md:col-span-3 xl:col-span-3 flex flex-col gap-2">
            <span id="statement-asset-type-label" class="font-medium">Tipo de ativo</span>
            <p-select name="assetType" ariaLabelledBy="statement-asset-type-label" optionLabel="label" optionValue="value" [fluid]="true" [options]="assetTypeOptions" [ngModel]="filter().assetType" (ngModelChange)="change('assetType', $event)" />
        </div>
        <div class="col-span-12 md:col-span-3 xl:col-span-3 flex flex-col gap-2">
            <span id="statement-status-label" class="font-medium">Situação</span>
            <p-select name="status" ariaLabelledBy="statement-status-label" optionLabel="label" optionValue="value" [fluid]="true" [options]="statusOptions" [ngModel]="filter().status" (ngModelChange)="change('status', $event)" />
        </div>
        <div class="col-span-6 md:col-span-3 flex flex-col gap-2">
            <label for="statement-from" class="font-medium">De</label>
            <p-datepicker inputId="statement-from" name="from" dateFormat="dd/mm/yy" appendTo="body" [showIcon]="true" [showClear]="true" [fluid]="true" [maxDate]="filter().to ?? today" [ngModel]="filter().from" (ngModelChange)="change('from', $event)" />
        </div>
        <div class="col-span-6 md:col-span-3 flex flex-col gap-2">
            <label for="statement-to" class="font-medium">Até</label>
            <p-datepicker inputId="statement-to" name="to" dateFormat="dd/mm/yy" appendTo="body" [showIcon]="true" [showClear]="true" [fluid]="true" [minDate]="filter().from" [maxDate]="today" [ngModel]="filter().to" (ngModelChange)="change('to', $event)" />
        </div>
        <div class="col-span-6 md:col-span-3 xl:col-span-2 flex flex-col gap-2">
            <label for="statement-min-total" class="font-medium">Total mínimo</label>
            <p-inputnumber appDecimalPaste inputId="statement-min-total" name="minTotal" mode="currency" currency="BRL" locale="pt-BR" [min]="0" [maxFractionDigits]="4" [fluid]="true" [invalid]="!valid()" [ngModel]="filter().minTotal" (ngModelChange)="change('minTotal', $event)" />
        </div>
        <div class="col-span-6 md:col-span-3 xl:col-span-2 flex flex-col gap-2">
            <label for="statement-max-total" class="font-medium">Total máximo</label>
            <p-inputnumber appDecimalPaste inputId="statement-max-total" name="maxTotal" mode="currency" currency="BRL" locale="pt-BR" [min]="0" [maxFractionDigits]="4" [fluid]="true" [invalid]="!valid()" [ngModel]="filter().maxTotal" (ngModelChange)="change('maxTotal', $event)" />
        </div>
        <div class="col-span-12 xl:col-span-2 flex flex-col gap-2 xl:self-end">
            <p-button label="Limpar filtros" icon="pi pi-filter-slash" severity="secondary" [outlined]="true" [fluid]="true" [disabled]="!active()" (onClick)="filter.set(empty)" />
        </div>
        @if (!valid()) {
            <div class="col-span-12">
                <p-message severity="error" size="small" variant="simple" text="O total mínimo não pode ser maior que o máximo." />
            </div>
        }
    </form>`
})
export class StatementFilters {
    readonly filter = model.required<StatementFilter>();

    protected readonly empty = EMPTY_STATEMENT_FILTER;
    protected readonly today = new Date();

    protected readonly typeOptions: { label: string; value: TransactionType | null }[] = [
        { label: 'Todos', value: null },
        ...(Object.keys(TRANSACTION_DISPLAY) as TransactionType[]).map((value) => ({ label: TRANSACTION_DISPLAY[value].label, value }))
    ];
    protected readonly assetTypeOptions: { label: string; value: AssetType | null }[] = [
        { label: 'Todos', value: null },
        { label: 'Ação', value: 'STOCK' },
        { label: 'Cripto', value: 'CRYPTO' }
    ];
    protected readonly statusOptions: { label: string; value: TransactionStatus | null }[] = [
        { label: 'Todas', value: null },
        { label: 'Válidas', value: 'ACTIVE' },
        { label: 'Estornadas', value: 'REVERSED' }
    ];

    protected readonly active = computed(() => hasActiveFilter(this.filter()));
    protected readonly valid = computed(() => isValidFilter(this.filter()));

    protected change<K extends keyof StatementFilter>(key: K, value: StatementFilter[K]): void {
        this.filter.update((filter) => ({ ...filter, [key]: value }));
    }
}
