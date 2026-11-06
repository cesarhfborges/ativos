import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TableModule } from 'primeng/table';
import { AssetType, DashboardPosition, MarketAsset, Position, WatchlistItem } from '@/app/core/api.models';
import { PlanSummary } from '@/app/shared/plan-summary';
import { PercentChange } from './percent-change';
import { ProfitLoss } from './profit-loss';
import { Sparkline } from './sparkline';

const ASSET_TYPE_LABELS: Record<AssetType, string> = { STOCK: 'Ação', CRYPTO: 'Cripto' };
/** Uma coleta perdida é normal; mais que isso, o preço da linha precisa dizer de quando é. */
const STALE_AFTER_MS = 2 * 60 * 60 * 1000;

type TypeFilter = AssetType | 'ALL';

@Component({
    selector: 'app-positions-table',
    host: { class: 'block' },
    imports: [ButtonModule, CurrencyPipe, DatePipe, DecimalPipe, FormsModule, IconFieldModule, InputIconModule, InputTextModule, PercentChange, PlanSummary, ProfitLoss, RouterLink, SelectButtonModule, SelectModule, Sparkline, TableModule],
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
            <div class="stack-table" [class]="market() ? 'stack-68' : 'stack-66'">
                <div class="flex flex-wrap items-center gap-4 mb-4">
                    <p-iconfield>
                        <p-inputicon class="pi pi-search" />
                        <input pInputText type="search" placeholder="Buscar ativo" aria-label="Buscar ativo por código ou nome" [ngModel]="search()" (ngModelChange)="search.set($event)" />
                    </p-iconfield>
                    <p-selectbutton ariaLabel="Filtrar por tipo de ativo" optionLabel="label" optionValue="value" [options]="typeOptions" [unselectable]="true" [ngModel]="typeFilter()" (ngModelChange)="typeFilter.set($event)" />
                    <div class="stack-only flex-col gap-2 w-full">
                        <span id="positions-sort-label" class="font-medium">Ordenar por</span>
                        <p-select ariaLabelledBy="positions-sort-label" optionLabel="label" optionValue="value" appendTo="body" [fluid]="true" [options]="sortOptions()" [ngModel]="sortKey()" (ngModelChange)="setSort($event)" />
                    </div>
                    <span class="text-muted-color ml-auto" aria-live="polite">{{ countLabel() }}</span>
                </div>

                <p-table [value]="rows()" dataKey="symbol" [sortField]="sort().field" [sortOrder]="sort().order" (onSort)="onTableSort($event)">
                    <ng-template #header>
                        <tr>
                            <th scope="col" pSortableColumn="symbol" class="whitespace-nowrap">Ativo <p-sortIcon field="symbol" /></th>
                            <th scope="col" pSortableColumn="quantity" class="text-right! whitespace-nowrap">Quantidade <p-sortIcon field="quantity" /></th>
                            @if (market()) {
                                <th scope="col" pSortableColumn="change24h" class="text-right! whitespace-nowrap">Cotação e 24h <p-sortIcon field="change24h" /></th>
                                <th scope="col" pSortableColumn="periodChange" class="text-right! whitespace-nowrap">Em {{ periodLabel() }} <p-sortIcon field="periodChange" /></th>
                                <th scope="col" pSortableColumn="rangePosition" class="stack-optional whitespace-nowrap">Mín. e máx. <p-sortIcon field="rangePosition" /></th>
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
                            <td class="stack-full">
                                <div class="font-medium">{{ row.symbol }}</div>
                                <div class="text-muted-color text-sm max-w-44 truncate" [title]="row.subtitle">{{ row.subtitle }}</div>
                                @if (planOf(row.symbol); as plan) {
                                    <div class="text-sm mt-1 max-w-56"><app-plan-summary [item]="plan" /></div>
                                }
                            </td>
                            <td class="text-right tabular-nums whitespace-nowrap" data-label="Quantidade">
                                <div>{{ row.quantity | number: '1.0-8' }}</div>
                                <div class="text-muted-color text-sm">Médio {{ row.averagePrice | currency: 'BRL' : 'symbol' : '1.2-8' }}</div>
                            </td>
                            <td class="text-right" [attr.data-label]="market() ? 'Cotação e 24h' : 'Cotação'">
                                @if (row.currentPrice !== null) {
                                    <div class="tabular-nums whitespace-nowrap">{{ row.currentPrice | currency: 'BRL' : 'symbol' : '1.2-8' }}</div>
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
                                <td class="text-right" [attr.data-label]="'Em ' + periodLabel()">
                                    <div class="flex flex-col items-end gap-1">
                                        <app-sparkline [values]="row.trend" [label]="'Tendência de ' + row.symbol + ' em ' + periodLabel()" />
                                        <span class="text-sm"><app-percent-change [value]="row.periodChange" /></span>
                                    </div>
                                </td>
                                <td class="stack-optional stack-full min-w-44" data-label="Mín. e máx.">
                                    @if (row.rangePosition !== null) {
                                        <div class="relative h-1 mt-1 mb-2 rounded-sm bg-linear-progress" aria-hidden="true">
                                            <div class="absolute -top-1 w-3 h-3 -ml-1.5 rounded-full bg-blue-600 dark:bg-blue-400 border-2 border-[#616161] dark:border-[#a8a8a8]" [style.left.%]="row.rangePosition"></div>
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
                            <td class="text-right" data-label="Valor a mercado">
                                <div class="font-medium tabular-nums whitespace-nowrap">{{ row.marketValue | currency: 'BRL' : 'symbol' : '1.2-2' }}</div>
                                <div class="text-muted-color text-sm tabular-nums whitespace-nowrap">Custo {{ row.investedValue | currency: 'BRL' : 'symbol' : '1.2-2' }}</div>
                            </td>
                            <td class="text-right" data-label="Lucro/Prejuízo">
                                @if (row.currentPrice !== null) {
                                    <app-profit-loss [stacked]="true" [value]="row.profitLoss" [percent]="row.profitLossPercent" />
                                } @else {
                                    <span class="text-muted-color">—</span>
                                }
                            </td>
                            <td class="text-right" data-label="% carteira">
                                <div class="tabular-nums">{{ row.share | number: '1.1-1' }}%</div>
                                <div class="h-1 mt-1.5 ml-auto w-16 rounded-sm bg-surface-200 dark:bg-surface-700" aria-hidden="true">
                                    <div class="h-full rounded-sm bg-blue-600 dark:bg-blue-400" [style.width.%]="row.share"></div>
                                </div>
                            </td>
                            @if (manageable()) {
                                <td class="stack-full whitespace-nowrap">
                                    <div class="stack-actions flex flex-col items-end">
                                        <p-button label="Comprar" size="small" [text]="true" [ariaLabel]="'Comprar mais ' + row.symbol" (onClick)="buy.emit(row)" />
                                        <p-button label="Vender" size="small" severity="secondary" [text]="true" [ariaLabel]="'Vender ' + row.symbol" (onClick)="sell.emit(row)" />
                                        <p-button label="Plano" size="small" severity="secondary" [text]="true" [ariaLabel]="(planOf(row.symbol) ? 'Editar plano de ' : 'Definir plano de ') + row.symbol" (onClick)="plan.emit(row)" />
                                    </div>
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
                                <th scope="row" class="text-left!" [attr.colspan]="market() ? 4 : 3">{{ totals().label }}</th>
                                @if (market()) {
                                    <!-- Acompanha a coluna opcional: sem ela, os totais sairiam do alinhamento. -->
                                    <td class="stack-optional"></td>
                                }
                                <td class="text-right" data-label="Valor a mercado">
                                    <div class="font-medium tabular-nums whitespace-nowrap">{{ totals().marketValue | currency: 'BRL' : 'symbol' : '1.2-2' }}</div>
                                    <div class="text-muted-color text-sm font-normal tabular-nums whitespace-nowrap">Custo {{ totals().invested | currency: 'BRL' : 'symbol' : '1.2-2' }}</div>
                                </td>
                                <td class="text-right" data-label="Lucro/Prejuízo"><app-profit-loss [stacked]="true" [value]="totals().profitLoss" [percent]="totals().profitLossPercent" /></td>
                                <td class="text-right tabular-nums" data-label="% carteira">{{ totals().share | number: '1.1-1' }}%</td>
                                @if (manageable()) {
                                    <td></td>
                                }
                            </tr>
                        }
                    </ng-template>
                </p-table>
            </div>
        }`,
    styles: `
        .bg-linear-progress {
            background: linear-gradient(90deg, #ff0000 15%, #0040ff 40%, #0040ff 65%, #3dcc00 85%);
        }
    `
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
    readonly plan = output<DashboardPosition>();
    /** Ativos acompanhados: quem tem plano mostra os alvos junto da posição. */
    readonly plans = input<WatchlistItem[]>([]);

    private readonly plansBySymbol = computed(() => new Map(this.plans().map((item) => [item.symbol, item])));
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

    protected planOf(symbol: string): WatchlistItem | undefined {
        return this.plansBySymbol().get(symbol);
    }

    protected readonly columnCount = computed(() => (this.market() ? 5 : 3) + 3 + (this.manageable() ? 1 : 0));

    /** O cabeçalho some quando a tabela vira blocos: a ordem é a mesma nos dois modos, para não mudar ao redimensionar. */
    protected readonly sort = signal({ field: 'marketValue', order: -1 });
    protected readonly sortKey = computed(() => `${this.sort().field}:${this.sort().order}`);
    protected readonly sortOptions = computed(() => {
        const options = [
            { label: 'Maior valor a mercado', value: 'marketValue:-1' },
            { label: 'Menor valor a mercado', value: 'marketValue:1' },
            { label: 'Ativo, de A a Z', value: 'symbol:1' },
            { label: 'Ativo, de Z a A', value: 'symbol:-1' },
            { label: 'Maior lucro', value: 'profitLoss:-1' },
            { label: 'Maior prejuízo', value: 'profitLoss:1' },
            { label: 'Maior peso na carteira', value: 'share:-1' }
        ];
        return this.market()
            ? [
                  ...options,
                  { label: 'Maior alta em 24h', value: 'change24h:-1' },
                  { label: 'Maior queda em 24h', value: 'change24h:1' },
                  { label: `Maior alta em ${this.periodLabel()}`, value: 'periodChange:-1' },
                  { label: `Maior queda em ${this.periodLabel()}`, value: 'periodChange:1' }
              ]
            : options;
    });

    protected setSort(key: string): void {
        const [field, order] = key.split(':');
        this.sort.set({ field, order: Number(order) });
    }

    /** Mantém o seletor em dia com a ordem escolhida pelo cabeçalho. */
    protected onTableSort(event: { field: string; order: number }): void {
        this.sort.set({ field: event.field, order: event.order });
    }
}
