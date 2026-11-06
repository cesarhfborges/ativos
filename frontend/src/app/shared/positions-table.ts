import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TableModule } from 'primeng/table';
import { AssetType, DashboardPosition, MarketAsset, Position } from '@/app/core/api.models';
import { PercentChange } from './percent-change';
import { ProfitLoss } from './profit-loss';
import { Sparkline } from './sparkline';

const ASSET_TYPE_LABELS: Record<AssetType, string> = { STOCK: 'Ação', CRYPTO: 'Cripto' };
/** Uma coleta perdida é normal; mais que isso, o preço da linha precisa dizer de quando é. */
const STALE_AFTER_MS = 2 * 60 * 60 * 1000;

type TypeFilter = AssetType | 'ALL';

@Component({
    selector: 'app-positions-table',
    imports: [ButtonModule, CurrencyPipe, DatePipe, DecimalPipe, FormsModule, IconFieldModule, InputIconModule, InputTextModule, PercentChange, ProfitLoss, RouterLink, SelectButtonModule, Sparkline, TableModule],
    template: `@if (positions().length === 0) {
            <p class="text-center text-muted-color py-8 m-0!">
                Você ainda não tem posições abertas.
                @if (manageable()) {
                    Registre sua primeira compra.
                } @else {
                    <a routerLink="/carteira" class="text-primary font-medium underline">Registre sua primeira compra na Carteira.</a>
                }
            </p>
        } @else {
            <div class="flex flex-wrap items-center gap-4 mb-4">
                <p-iconfield>
                    <p-inputicon class="pi pi-search" />
                    <input pInputText type="search" placeholder="Buscar ativo" aria-label="Buscar ativo por código ou nome" [ngModel]="search()" (ngModelChange)="search.set($event)" />
                </p-iconfield>
                <p-selectbutton ariaLabel="Filtrar por tipo de ativo" optionLabel="label" optionValue="value" [options]="typeOptions" [unselectable]="true" [ngModel]="typeFilter()" (ngModelChange)="typeFilter.set($event)" />
                <span class="text-muted-color ml-auto" aria-live="polite">{{ countLabel() }}</span>
            </div>

            <p-table [value]="rows()" dataKey="symbol" sortField="marketValue" [sortOrder]="-1" [tableStyle]="{ 'min-width': tableWidth() }">
                <ng-template #header>
                    <tr>
                        <th scope="col" pSortableColumn="symbol" class="whitespace-nowrap">Ativo <p-sortIcon field="symbol" /></th>
                        <th scope="col" pSortableColumn="quantity" class="text-right! whitespace-nowrap">Quantidade <p-sortIcon field="quantity" /></th>
                        @if (market()) {
                            <th scope="col" pSortableColumn="change24h" class="text-right! whitespace-nowrap">Cotação e 24h <p-sortIcon field="change24h" /></th>
                            <th scope="col" pSortableColumn="periodChange" class="text-right! whitespace-nowrap">Em {{ periodLabel() }} <p-sortIcon field="periodChange" /></th>
                            <th scope="col" pSortableColumn="rangePosition" class="whitespace-nowrap">Mín. e máx. <p-sortIcon field="rangePosition" /></th>
                        } @else {
                            <th scope="col" pSortableColumn="currentPrice" class="text-right! whitespace-nowrap">Cotação <p-sortIcon field="currentPrice" /></th>
                        }
                        <th scope="col" pSortableColumn="marketValue" class="text-right! whitespace-nowrap">Valor a mercado <p-sortIcon field="marketValue" /></th>
                        <th scope="col" pSortableColumn="profitLoss" class="text-right! whitespace-nowrap">Lucro/Prejuízo <p-sortIcon field="profitLoss" /></th>
                        <th scope="col" pSortableColumn="share" class="text-right! whitespace-nowrap">% carteira <p-sortIcon field="share" /></th>
                        @if (manageable()) {
                            <th scope="col" class="text-right! whitespace-nowrap">Ações</th>
                        }
                    </tr>
                </ng-template>
                <ng-template #body let-row>
                    <tr>
                        <td>
                            <div class="font-medium">{{ row.symbol }}</div>
                            <div class="text-muted-color text-sm max-w-44 truncate" [title]="row.subtitle">{{ row.subtitle }}</div>
                        </td>
                        <td class="text-right tabular-nums whitespace-nowrap">
                            <div>{{ row.quantity | number: '1.0-8' }}</div>
                            <div class="text-muted-color text-sm">Médio {{ row.averagePrice | currency: 'BRL' : 'symbol' : '1.2-4' }}</div>
                        </td>
                        <td class="text-right">
                            @if (row.currentPrice !== null) {
                                <div class="tabular-nums whitespace-nowrap">{{ row.currentPrice | currency: 'BRL' : 'symbol' : '1.2-4' }}</div>
                                @if (market()) {
                                    <div class="text-sm"><app-percent-change [value]="row.change24h" /></div>
                                }
                                @if (!market() || row.stale) {
                                    <div class="text-muted-color text-sm">
                                        @if (row.stale) {
                                            <i class="pi pi-clock mr-1 text-xs!" aria-hidden="true"></i>
                                        }
                                        {{ row.quoteUpdatedAt | date: 'short' }}
                                    </div>
                                }
                            } @else {
                                <span class="text-muted-color whitespace-nowrap"><i class="pi pi-exclamation-circle mr-1" aria-hidden="true"></i>Sem cotação</span>
                            }
                        </td>
                        @if (market()) {
                            <td class="text-right">
                                <div class="flex items-center justify-end gap-3">
                                    <app-sparkline [values]="row.trend" [label]="'Tendência de ' + row.symbol + ' em ' + periodLabel()" />
                                    <span class="text-sm w-20"><app-percent-change [value]="row.periodChange" /></span>
                                </div>
                            </td>
                            <td class="min-w-44">
                                @if (row.rangePosition !== null) {
                                    <div class="relative h-1 mt-1 mb-2 rounded-sm bg-surface-200 dark:bg-surface-700" aria-hidden="true">
                                        <div class="absolute -top-1 w-3 h-3 -ml-1.5 rounded-full bg-blue-600 dark:bg-blue-400 border-2 border-surface-0 dark:border-surface-900" [style.left.%]="row.rangePosition"></div>
                                    </div>
                                    <div class="flex justify-between gap-2 text-muted-color text-xs tabular-nums whitespace-nowrap">
                                        <span><span class="sr-only">Mínimo </span>{{ row.periodLow | currency: 'BRL' : 'symbol' : '1.2-2' }}</span>
                                        <span><span class="sr-only">Máximo </span>{{ row.periodHigh | currency: 'BRL' : 'symbol' : '1.2-2' }}</span>
                                    </div>
                                } @else {
                                    <span class="text-muted-color" aria-hidden="true">—</span><span class="sr-only">Sem dados</span>
                                }
                            </td>
                        }
                        <td class="text-right">
                            <div class="font-medium tabular-nums whitespace-nowrap">{{ row.marketValue | currency: 'BRL' : 'symbol' : '1.2-2' }}</div>
                            <div class="text-muted-color text-sm tabular-nums whitespace-nowrap">Custo {{ row.investedValue | currency: 'BRL' : 'symbol' : '1.2-2' }}</div>
                        </td>
                        <td class="text-right">
                            @if (row.currentPrice !== null) {
                                <app-profit-loss [stacked]="true" [value]="row.profitLoss" [percent]="row.profitLossPercent" />
                            } @else {
                                <span class="text-muted-color">—</span>
                            }
                        </td>
                        <td class="text-right">
                            <div class="tabular-nums">{{ row.share | number: '1.1-1' }}%</div>
                            <div class="h-1 mt-1 ml-auto w-16 rounded-sm bg-surface-200 dark:bg-surface-700" aria-hidden="true">
                                <div class="h-full rounded-sm bg-blue-600 dark:bg-blue-400" [style.width.%]="row.share"></div>
                            </div>
                        </td>
                        @if (manageable()) {
                            <td class="text-right whitespace-nowrap">
                                <p-button label="Comprar" size="small" [text]="true" [ariaLabel]="'Comprar mais ' + row.symbol" (onClick)="buy.emit(row)" />
                                <p-button label="Vender" size="small" severity="secondary" [text]="true" [ariaLabel]="'Vender ' + row.symbol" (onClick)="sell.emit(row)" />
                            </td>
                        }
                    </tr>
                </ng-template>
                <ng-template #emptymessage>
                    <tr>
                        <td class="text-center text-muted-color py-8!" [attr.colspan]="columnCount()">Nenhuma posição encontrada com estes filtros.</td>
                    </tr>
                </ng-template>
                <ng-template #footer>
                    @if (rows().length > 0) {
                        <tr>
                            <th scope="row" class="text-left!" [attr.colspan]="columnsBeforeValue()">{{ totals().label }}</th>
                            <td class="text-right">
                                <div class="font-medium tabular-nums whitespace-nowrap">{{ totals().marketValue | currency: 'BRL' : 'symbol' : '1.2-2' }}</div>
                                <div class="text-muted-color text-sm font-normal tabular-nums whitespace-nowrap">Custo {{ totals().invested | currency: 'BRL' : 'symbol' : '1.2-2' }}</div>
                            </td>
                            <td class="text-right"><app-profit-loss [stacked]="true" [value]="totals().profitLoss" [percent]="totals().profitLossPercent" /></td>
                            <td class="text-right tabular-nums">{{ totals().share | number: '1.1-1' }}%</td>
                            @if (manageable()) {
                                <td></td>
                            }
                        </tr>
                    }
                </ng-template>
            </p-table>
        }`
})
export class PositionsTable {
    /** Com as ações de comprar e vender, que só existem na Carteira. */
    readonly manageable = input(false);
    readonly positions = input.required<DashboardPosition[]>();
    /** Patrimônio usado como base do percentual de cada posição. */
    readonly total = input.required<number>();
    /** Com dados de mercado, a tabela ganha as colunas de variação e faixa de preço. */
    readonly market = input<MarketAsset[] | null>(null);
    /** Período dos dados de mercado, como "30 dias". */
    readonly periodLabel = input('');
    readonly buy = output<Position>();
    readonly sell = output<Position>();

    protected readonly search = signal('');
    protected readonly typeFilter = signal<TypeFilter>('ALL');
    protected readonly typeOptions: { label: string; value: TypeFilter }[] = [
        { label: 'Todos', value: 'ALL' },
        { label: 'Ações', value: 'STOCK' },
        { label: 'Cripto', value: 'CRYPTO' }
    ];

    /** Base do aviso de cotação antiga: a coleta mais recente, e não o relógio, para não marcar tudo quando a coleta para. */
    private readonly newestQuote = computed(() => Math.max(0, ...this.positions().map((position) => (position.quoteUpdatedAt ? Date.parse(position.quoteUpdatedAt) : 0))));

    private readonly allRows = computed(() => {
        const marketBySymbol = new Map((this.market() ?? []).map((asset) => [asset.symbol, asset]));
        return this.positions().map((position) => {
            const asset = marketBySymbol.get(position.symbol);
            const low = asset?.periodLow ?? null;
            const high = asset?.periodHigh ?? null;
            return {
                ...position,
                subtitle: position.name === position.symbol ? ASSET_TYPE_LABELS[position.assetType] : `${position.name} · ${ASSET_TYPE_LABELS[position.assetType]}`,
                share: this.total() > 0 ? (position.marketValue / this.total()) * 100 : 0,
                stale: position.quoteUpdatedAt !== null && this.newestQuote() - Date.parse(position.quoteUpdatedAt) > STALE_AFTER_MS,
                change24h: asset?.change24hPercent ?? null,
                periodChange: asset?.periodChangePercent ?? null,
                periodLow: low,
                periodHigh: high,
                rangePosition: position.currentPrice !== null && low !== null && high !== null && high > low ? Math.min(100, Math.max(0, ((position.currentPrice - low) / (high - low)) * 100)) : null,
                trend: asset?.prices.map((point) => point.price) ?? []
            };
        });
    });

    protected readonly rows = computed(() => {
        const term = this.search().trim().toLocaleLowerCase('pt-BR');
        const type = this.typeFilter();
        return this.allRows().filter((row) => (type === 'ALL' || row.assetType === type) && (!term || row.symbol.toLocaleLowerCase('pt-BR').includes(term) || row.name.toLocaleLowerCase('pt-BR').includes(term)));
    });

    protected readonly countLabel = computed(() => {
        const shown = this.rows().length;
        const all = this.allRows().length;
        if (shown === all) {
            return all === 1 ? '1 posição' : `${all} posições`;
        }
        return `${shown} de ${all} posições`;
    });

    protected readonly totals = computed(() => {
        const rows = this.rows();
        const invested = rows.reduce((sum, row) => sum + row.investedValue, 0);
        const marketValue = rows.reduce((sum, row) => sum + row.marketValue, 0);
        const profitLoss = marketValue - invested;
        return {
            label: rows.length === this.allRows().length ? 'Total' : 'Total filtrado',
            invested,
            marketValue,
            profitLoss,
            profitLossPercent: invested > 0 ? (profitLoss / invested) * 100 : 0,
            share: rows.reduce((sum, row) => sum + row.share, 0)
        };
    });

    protected readonly columnsBeforeValue = computed(() => (this.market() ? 5 : 3));
    protected readonly columnCount = computed(() => this.columnsBeforeValue() + 3 + (this.manageable() ? 1 : 0));
    protected readonly tableWidth = computed(() => `${(this.market() ? 70 : 48) + (this.manageable() ? 12 : 0)}rem`);
}
