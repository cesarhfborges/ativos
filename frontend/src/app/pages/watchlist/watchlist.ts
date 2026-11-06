import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, of, switchMap, tap, timer } from 'rxjs';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { AssetCandidate, AssetType, Position, Transaction, WatchlistItem } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';
import { ConfirmService } from '@/app/core/confirm.service';
import { MARKET_PERIODS, shortHistoryNote } from '@/app/core/market';
import { signalReading } from '@/app/core/technical-signal';
import { isHeld, reachedTargets } from '@/app/core/watchlist';
import { WatchlistService } from '@/app/core/watchlist.service';
import { BuyDialog } from '@/app/pages/wallet/components/buy-dialog';
import { PercentChange } from '@/app/shared/percent-change';
import { PlanSummary } from '@/app/shared/plan-summary';
import { Sparkline } from '@/app/shared/sparkline';
import { WatchlistChart } from './components/watchlist-chart';
import { WatchlistPlanDialog } from './components/watchlist-plan-dialog';

type TypeFilter = 'ALL' | AssetType;

const NEW_SYMBOL_RELOAD_MS = 3000;

interface WatchlistSort {
    field: string;
    order: number;
}

const DEFAULT_SORT: WatchlistSort = { field: 'symbol', order: 1 };

const TYPE_LABELS: Record<AssetType, string> = { STOCK: 'Ação', CRYPTO: 'Cripto' };

/** Ativos que o usuário quer observar, com ou sem posição. Nada aqui altera a carteira nem o patrimônio. */
@Component({
    selector: 'app-watchlist',
    imports: [
        BuyDialog,
        ButtonModule,
        CurrencyPipe,
        DatePipe,
        DecimalPipe,
        FormsModule,
        IconFieldModule,
        InputIconModule,
        InputTextModule,
        MessageModule,
        PercentChange,
        PlanSummary,
        SelectButtonModule,
        SelectModule,
        SkeletonModule,
        Sparkline,
        TableModule,
        TagModule,
        WatchlistChart,
        WatchlistPlanDialog
    ],
    template: `
        <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
            <div>
                <h1 class="text-3xl! font-semibold m-0!">Acompanhamento</h1>
                <p class="text-surface-600 dark:text-surface-300 m-0! mt-1!">Observe ativos sem precisar comprá-los e defina o que você espera de cada um.</p>
            </div>
            <p-button label="Adicionar ativo" icon="pi pi-plus" (onClick)="openAdd()" />
        </div>

        @if (failure(); as message) {
            <div class="card">
                <p-message severity="error" [text]="message" styleClass="mb-4" />
                <p-button label="Tentar novamente" icon="pi pi-refresh" (onClick)="reload()" />
            </div>
        } @else if (items(); as all) {
            <section class="card mb-0!" aria-labelledby="watchlist-title">
                <h2 id="watchlist-title" class="sr-only">Ativos acompanhados</h2>
                <div class="stack-table stack-74">
                    @if (all.length === 0) {
                        <p class="text-center text-surface-600 dark:text-surface-300 py-8 m-0!">Você ainda não acompanha nenhum ativo. Adicione o primeiro para ver a cotação e definir seus alvos.</p>
                    } @else {
                        @if (reachedCount() > 0) {
                            <p-message severity="warn" icon="pi pi-flag-fill" styleClass="mb-4" [text]="reachedMessage()" />
                        }
                        <div class="flex flex-wrap items-center gap-4 mb-4">
                            <p-iconfield>
                                <p-inputicon class="pi pi-search" />
                                <input pInputText type="search" placeholder="Buscar ativo" aria-label="Buscar ativo por código ou nome" [ngModel]="search()" (ngModelChange)="search.set($event)" />
                            </p-iconfield>
                            <p-selectbutton ariaLabel="Filtrar por tipo de ativo" optionLabel="label" optionValue="value" [options]="typeOptions" [unselectable]="true" [ngModel]="typeFilter()" (ngModelChange)="typeFilter.set($event)" />
                            <p-selectbutton ariaLabel="Período da análise" optionLabel="label" optionValue="value" [options]="periods" [unselectable]="true" [ngModel]="days()" (ngModelChange)="days.set($event)" />
                            <div class="stack-only flex-col gap-2 w-full">
                                <span id="watchlist-sort-label" class="font-medium">Ordenar por</span>
                                <p-select ariaLabelledBy="watchlist-sort-label" optionLabel="label" optionValue="value" appendTo="body" [fluid]="true" [options]="sortOptions()" [ngModel]="sortKey()" (ngModelChange)="setSort($event)" />
                            </div>
                            <span class="text-surface-600 dark:text-surface-300 ml-auto" aria-live="polite">{{ countLabel() }}</span>
                        </div>

                        <div class="transition-opacity" [class.opacity-60]="periodLoading()" [attr.aria-busy]="periodLoading()">
                            <p-table dataKey="id" [value]="rows()" [sortField]="sort().field" [sortOrder]="sort().order" (onSort)="onTableSort($event)">
                                <ng-template #header>
                                    <tr>
                                        <th scope="col" class="w-12"><span class="sr-only">Detalhes</span></th>
                                        <th scope="col" pSortableColumn="symbol" class="whitespace-nowrap">Ativo <p-sortIcon field="symbol" /></th>
                                        <th scope="col" pSortableColumn="change24hPercent" class="text-right! whitespace-nowrap">Cotação e 24h <p-sortIcon field="change24hPercent" /></th>
                                        <th scope="col" pSortableColumn="periodChangePercent" class="text-right! whitespace-nowrap">Em {{ periodLabel() }} <p-sortIcon field="periodChangePercent" /></th>
                                        @if (signalsEnabled()) {
                                            <th scope="col" pSortableColumn="rsi" class="whitespace-nowrap">Sinal técnico <p-sortIcon field="rsi" /></th>
                                        }
                                        <th scope="col" class="whitespace-nowrap">Seu plano</th>
                                        <th scope="col" pSortableColumn="quantity" class="text-right! whitespace-nowrap">Na carteira <p-sortIcon field="quantity" /></th>
                                        <th scope="col" class="text-right! whitespace-nowrap">Ações</th>
                                    </tr>
                                </ng-template>
                                <ng-template #body let-row let-expanded="expanded">
                                    <tr>
                                        <td class="stack-toggle">
                                            <button
                                                type="button"
                                                pButton
                                                [pRowToggler]="row"
                                                [text]="true"
                                                [rounded]="true"
                                                severity="secondary"
                                                [attr.aria-label]="(expanded ? 'Ocultar detalhes de ' : 'Mostrar detalhes de ') + row.symbol"
                                                [attr.aria-expanded]="expanded"
                                            >
                                                <i class="pi" [class.pi-chevron-down]="expanded" [class.pi-chevron-right]="!expanded" pButtonIcon aria-hidden="true"></i>
                                            </button>
                                        </td>
                                        <td class="stack-full">
                                            <div class="font-medium">{{ row.symbol }}</div>
                                            <div class="text-surface-600 dark:text-surface-300 text-sm max-w-44 truncate" [title]="row.name">{{ row.name === row.symbol ? typeLabel(row.assetType) : row.name + ' · ' + typeLabel(row.assetType) }}</div>
                                        </td>
                                        <td class="text-right" data-label="Cotação e 24h">
                                            <div>
                                                @if (row.currentPrice !== null) {
                                                    <div class="tabular-nums whitespace-nowrap">{{ row.currentPrice | currency: 'BRL' : 'symbol' : '1.2-8' }}</div>
                                                    <div class="text-sm"><app-percent-change [value]="row.change24hPercent" /></div>
                                                } @else {
                                                    <span class="text-surface-600 dark:text-surface-300 whitespace-nowrap"><i class="pi pi-exclamation-circle mr-1" aria-hidden="true"></i>Sem cotação</span>
                                                }
                                            </div>
                                        </td>
                                        <td class="text-right" [attr.data-label]="'Em ' + periodLabel()">
                                            <div class="flex flex-col items-end gap-1">
                                                <app-sparkline [values]="row.trend" [label]="'Tendência de ' + row.symbol + ' em ' + periodLabel()" />
                                                <span class="text-sm"><app-percent-change [value]="row.periodChangePercent" /></span>
                                                @if (row.periodNote; as note) {
                                                    <span class="text-muted-color text-xs whitespace-nowrap">{{ note }}</span>
                                                }
                                            </div>
                                        </td>
                                        @if (signalsEnabled()) {
                                            <td data-label="Sinal técnico">
                                                <div>
                                                    @if (row.reading; as reading) {
                                                        <p-tag [value]="reading.label" [icon]="reading.icon" [severity]="reading.severity" />
                                                        @if (row.rsi !== null) {
                                                            <div class="text-surface-600 dark:text-surface-300 text-sm mt-1 whitespace-nowrap">RSI {{ row.rsi | number: '1.1-1' }} · {{ row.signal?.trend === 'UP' ? 'alta' : 'baixa' }}</div>
                                                        }
                                                    }
                                                </div>
                                            </td>
                                        }
                                        <td class="stack-full" data-label="Seu plano">
                                            <div class="min-w-0">
                                                <app-plan-summary [item]="row" />
                                                @if (row.note) {
                                                    <div class="text-surface-600 dark:text-surface-300 text-sm wrap-anywhere mt-1 line-clamp-2" [title]="row.note">{{ row.note }}</div>
                                                }
                                            </div>
                                        </td>
                                        <td class="text-right tabular-nums whitespace-nowrap" data-label="Na carteira">
                                            <div>
                                                @if (row.quantity > 0) {
                                                    <div>{{ row.quantity | number: '1.0-8' }}</div>
                                                    <div class="text-surface-600 dark:text-surface-300 text-sm">Médio {{ row.averagePrice | currency: 'BRL' : 'symbol' : '1.2-8' }}</div>
                                                } @else {
                                                    <span class="text-surface-600 dark:text-surface-300" aria-hidden="true">—</span><span class="sr-only">Sem posição</span>
                                                }
                                            </div>
                                        </td>
                                        <td class="whitespace-nowrap">
                                            <div class="stack-actions flex flex-col items-end">
                                                <p-button label="Comprar" size="small" [text]="true" [ariaLabel]="'Registrar compra de ' + row.symbol" (onClick)="openBuy(row)" />
                                                <p-button label="Plano" size="small" severity="secondary" [text]="true" [ariaLabel]="'Editar plano de ' + row.symbol" (onClick)="openEdit(row)" />
                                                <p-button icon="pi pi-trash" size="small" severity="danger" [text]="true" [ariaLabel]="'Deixar de acompanhar ' + row.symbol" (onClick)="remove(row)" />
                                            </div>
                                        </td>
                                    </tr>
                                </ng-template>
                                <ng-template #expandedrow let-row>
                                    <tr>
                                        <td [attr.colspan]="columnCount()">
                                            @if (row.reading; as reading) {
                                                <section class="mb-6" [attr.aria-label]="'Sinal técnico de ' + row.symbol">
                                                    <h3 class="text-base! font-semibold mt-0! mb-2!">Sinal técnico</h3>
                                                    <p class="m-0! mb-3!">{{ reading.reason }}</p>
                                                    @if (row.signal?.available) {
                                                        <dl class="grid grid-cols-2 lg:grid-cols-4 gap-4 m-0">
                                                            <div>
                                                                <dt class="text-surface-600 dark:text-surface-300 text-sm">RSI de 14 dias</dt>
                                                                <dd class="m-0 font-medium tabular-nums">{{ row.rsi | number: '1.1-1' }}</dd>
                                                            </div>
                                                            <div>
                                                                <dt class="text-surface-600 dark:text-surface-300 text-sm">Média de 20 dias</dt>
                                                                <dd class="m-0 font-medium tabular-nums">{{ row.signal?.shortAverage | currency: 'BRL' : 'symbol' : '1.2-2' }}</dd>
                                                            </div>
                                                            <div>
                                                                <dt class="text-surface-600 dark:text-surface-300 text-sm">Média de 50 dias</dt>
                                                                <dd class="m-0 font-medium tabular-nums">{{ row.signal?.longAverage | currency: 'BRL' : 'symbol' : '1.2-2' }}</dd>
                                                            </div>
                                                            <div>
                                                                <dt class="text-surface-600 dark:text-surface-300 text-sm">Dados até</dt>
                                                                <dd class="m-0 font-medium">{{ row.signal?.asOf | date: 'short' }}</dd>
                                                            </div>
                                                        </dl>
                                                    }
                                                </section>
                                            }
                                            <app-watchlist-chart [item]="row" [days]="days()" [periodLabel]="periodLabel()" />
                                            @if (row.quoteUpdatedAt) {
                                                <p class="text-surface-600 dark:text-surface-300 text-sm mt-4! mb-0!">Última cotação em {{ row.quoteUpdatedAt | date: 'short' }}.</p>
                                            }
                                            @if (row.note) {
                                                <p class="mt-2! mb-0! whitespace-pre-line wrap-anywhere"><strong>Nota:</strong> {{ row.note }}</p>
                                            }
                                        </td>
                                    </tr>
                                </ng-template>
                                <ng-template #emptymessage>
                                    <tr>
                                        <td [attr.colspan]="columnCount()" class="text-center! text-surface-600 dark:text-surface-300 py-8!">Nenhum ativo encontrado com estes filtros.</td>
                                    </tr>
                                </ng-template>
                            </p-table>
                        </div>
                        @if (signalsEnabled()) {
                            <p class="text-surface-600 dark:text-surface-300 text-sm mt-4! mb-0!">O sinal técnico usa os fechamentos diários e descreve o histórico de preços. Ele não prevê o futuro nem é recomendação de investimento.</p>
                        }
                    }
                </div>
            </section>
        } @else {
            <p-skeleton height="20rem" />
        }

        <app-watchlist-plan-dialog [item]="editing()" [(visible)]="planVisible" (saved)="onSaved($event)" (closed)="restoreFocus()" />
        <app-buy-dialog [position]="buyPosition()" [asset]="buyAsset()" [(visible)]="buyVisible" (completed)="onBought($event)" (closed)="restoreFocus()" />
    `
})
export class Watchlist {
    private readonly watchlist = inject(WatchlistService);
    private readonly messages = inject(MessageService);
    private readonly confirm = inject(ConfirmService);
    private readonly destroyRef = inject(DestroyRef);

    private returnFocusTo: HTMLElement | null = null;

    protected readonly periods = MARKET_PERIODS;
    protected readonly typeOptions: { label: string; value: TypeFilter }[] = [
        { label: 'Todos', value: 'ALL' },
        { label: 'Ações', value: 'STOCK' },
        { label: 'Cripto', value: 'CRYPTO' }
    ];

    /** Nulo até a primeira resposta. */
    protected readonly items = signal<WatchlistItem[] | null>(null);
    protected readonly failure = signal<string | null>(null);
    protected readonly days = signal(30);
    protected readonly periodLoading = signal(false);
    protected readonly search = signal('');
    protected readonly typeFilter = signal<TypeFilter>('ALL');
    private readonly reloads = signal(0);
    /** O cabeçalho some quando a tabela vira blocos: a ordem é a mesma nos dois modos, para não mudar ao redimensionar. */
    protected readonly sort = signal<WatchlistSort>(DEFAULT_SORT);
    protected readonly sortKey = computed(() => `${this.sort().field}:${this.sort().order}`);

    protected readonly planVisible = signal(false);
    protected readonly editing = signal<WatchlistItem | null>(null);
    protected readonly buyVisible = signal(false);
    protected readonly buyPosition = signal<Position | null>(null);
    protected readonly buyAsset = signal<AssetCandidate | null>(null);

    protected readonly periodLabel = computed(() => MARKET_PERIODS.find((period) => period.value === this.days())?.label ?? '');

    protected readonly rows = computed(() => {
        const term = this.search().trim().toLocaleLowerCase('pt-BR');
        const type = this.typeFilter();
        return (this.items() ?? [])
            .filter((item) => (type === 'ALL' || item.assetType === type) && (!term || item.symbol.toLocaleLowerCase('pt-BR').includes(term) || item.name.toLocaleLowerCase('pt-BR').includes(term)))
            .map((item) => ({ ...item, trend: item.prices.map((point) => point.price), periodNote: item.periodChangePercent === null ? null : shortHistoryNote(item.prices, this.days()), rsi: item.signal?.rsi ?? null, reading: item.signal ? signalReading(item.signal) : null }));
    });

    /** Com o módulo de projeções desligado, nenhum item traz o sinal, e a coluna some. */
    protected readonly signalsEnabled = computed(() => (this.items() ?? []).some((item) => item.signal !== null));
    protected readonly columnCount = computed(() => (this.signalsEnabled() ? 8 : 7));

    protected readonly countLabel = computed(() => {
        const shown = this.rows().length;
        const all = this.items()?.length ?? 0;
        return shown === all ? (all === 1 ? '1 ativo' : `${all} ativos`) : `${shown} de ${all} ativos`;
    });

    protected readonly reachedCount = computed(() => reachedTargets(this.items() ?? []).length);
    protected readonly reachedMessage = computed(() => (this.reachedCount() === 1 ? '1 alvo do seu plano foi atingido.' : `${this.reachedCount()} alvos do seu plano foram atingidos.`));

    constructor() {
        // switchMap descarta a resposta do período anterior, que poderia chegar depois da do atual.
        toObservable(computed(() => ({ days: this.days(), reloads: this.reloads() })))
            .pipe(
                tap(() => this.periodLoading.set(true)),
                switchMap(({ days }) =>
                    this.watchlist.list(days).pipe(
                        catchError((error: unknown) => {
                            this.failure.set(errorMessage(error));
                            return of(null);
                        })
                    )
                ),
                takeUntilDestroyed()
            )
            .subscribe((items) => {
                this.periodLoading.set(false);
                if (items) {
                    this.items.set(items);
                    this.failure.set(null);
                }
            });
    }

    protected readonly sortOptions = computed(() => {
        const options = [
            { label: 'Ativo, de A a Z', value: 'symbol:1' },
            { label: 'Ativo, de Z a A', value: 'symbol:-1' },
            { label: 'Maior alta em 24h', value: 'change24hPercent:-1' },
            { label: 'Maior queda em 24h', value: 'change24hPercent:1' },
            { label: `Maior alta em ${this.periodLabel()}`, value: 'periodChangePercent:-1' },
            { label: `Maior queda em ${this.periodLabel()}`, value: 'periodChangePercent:1' },
            { label: 'Maior quantidade na carteira', value: 'quantity:-1' },
            { label: 'Menor quantidade na carteira', value: 'quantity:1' }
        ];
        return this.signalsEnabled() ? [...options, { label: 'Maior RSI', value: 'rsi:-1' }, { label: 'Menor RSI', value: 'rsi:1' }] : options;
    });

    protected setSort(key: string): void {
        const [field, order] = key.split(':');
        this.sort.set({ field, order: Number(order) });
    }

    /** Mantém o seletor em dia com a ordem escolhida pelo cabeçalho. */
    protected onTableSort(event: { field: string; order: number }): void {
        this.sort.set({ field: event.field, order: event.order });
    }

    protected reload(): void {
        this.reloads.update((count) => count + 1);
    }

    protected typeLabel(type: AssetType): string {
        return TYPE_LABELS[type];
    }

    protected openAdd(): void {
        this.rememberFocus();
        this.editing.set(null);
        this.planVisible.set(true);
    }

    protected openEdit(item: WatchlistItem): void {
        this.rememberFocus();
        this.editing.set(item);
        this.planVisible.set(true);
    }

    /** Quem já tem o ativo vê o preço médio; quem não tem começa a compra com os dados preenchidos. */
    protected openBuy(item: WatchlistItem): void {
        this.rememberFocus();
        const { symbol, name, assetType, quantity, averagePrice, currentPrice } = item;
        this.buyPosition.set(isHeld(item) && averagePrice !== null ? { symbol, name, assetType, quantity, averagePrice, currentPrice } : null);
        this.buyAsset.set(isHeld(item) ? null : { symbol, name, assetType, price: currentPrice });
        this.buyVisible.set(true);
    }

    protected remove(item: WatchlistItem): void {
        this.confirm.ask(
            {
                header: 'Deixar de acompanhar',
                message: `Remover ${item.symbol} do acompanhamento? O plano e a nota serão apagados. Sua carteira não muda.`,
                acceptLabel: 'Remover',
                danger: true
            },
            () =>
                this.watchlist.remove(item.id).subscribe({
                    next: () => {
                        this.messages.add({ severity: 'success', closable: false, summary: `${item.symbol} removido do acompanhamento` });
                        this.reload();
                    },
                    error: (error: unknown) => this.messages.add({ severity: 'error', closable: false, summary: 'Não foi possível remover', detail: errorMessage(error) })
                })
        );
    }

    protected onSaved(item: WatchlistItem): void {
        this.messages.add({ severity: 'success', closable: false, summary: this.editing() ? `Plano de ${item.symbol} salvo` : `${item.symbol} adicionado ao acompanhamento` });
        this.reload();
        // A cotação de um símbolo novo é buscada logo depois de salvar, sem atrasar a resposta.
        if (item.currentPrice === null) {
            timer(NEW_SYMBOL_RELOAD_MS)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe(() => this.reload());
        }
    }

    protected onBought(transaction: Transaction): void {
        this.messages.add({ severity: 'success', closable: false, summary: `Compra de ${transaction.symbol} registrada` });
        this.reload();
    }

    protected restoreFocus(): void {
        this.returnFocusTo?.focus();
        this.returnFocusTo = null;
    }

    private rememberFocus(): void {
        this.returnFocusTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    }
}
