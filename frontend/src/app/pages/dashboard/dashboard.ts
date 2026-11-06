import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, forkJoin, of, switchMap, tap } from 'rxjs';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SelectButtonModule } from 'primeng/selectbutton';
import { SkeletonModule } from 'primeng/skeleton';
import { DashboardOverview, DashboardPosition, MarketAsset, PortfolioHistoryPoint, Transaction, WatchlistItem } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';
import { composition, MARKET_PERIODS, periodChanges, resultByAsset } from '@/app/core/market';
import { allocation } from '@/app/core/portfolio';
import { portfolioRadar } from '@/app/core/portfolio-radar';
import { WatchlistService } from '@/app/core/watchlist.service';
import { WalletService } from '@/app/core/wallet.service';
import { PositionsTable } from '@/app/shared/positions-table';
import { ProfitLoss } from '@/app/shared/profit-loss';
import { RecentTransactions } from '@/app/shared/recent-transactions';
import { StatCard } from '@/app/shared/stat-card';
import { AllocationChart } from './components/allocation-chart';
import { AssetPerformanceChart } from './components/asset-performance-chart';
import { HistoryChart } from './components/history-chart';
import { MarketHighlights } from './components/market-highlights';
import { PortfolioRadarChart } from './components/portfolio-radar-chart';
import { RankedBars } from './components/ranked-bars';
import { ReachedTargets } from './components/reached-targets';
import { ReturnChart } from './components/return-chart';

const RECENT_SIZE = 5;

const PERIODS = MARKET_PERIODS;

const signed = (value: number, text: string) => `${value > 0 ? '+' : value < 0 ? '−' : ''}${text}`;
const decimal = (value: number, digits: number) => Math.abs(value).toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });

@Component({
    selector: 'app-dashboard',
    imports: [
        AllocationChart,
        AssetPerformanceChart,
        ButtonModule,
        DatePipe,
        FormsModule,
        HistoryChart,
        MarketHighlights,
        MessageModule,
        PortfolioRadarChart,
        PositionsTable,
        ProfitLoss,
        RankedBars,
        ReachedTargets,
        RecentTransactions,
        ReturnChart,
        RouterLink,
        SelectButtonModule,
        SkeletonModule,
        StatCard
    ],
    template: `
        <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
            <div>
                <h1 class="text-3xl! font-semibold m-0!">Painel</h1>
                @if (overview()?.quotesUpdatedAt; as updatedAt) {
                    <p class="text-surface-600 dark:text-surface-300 m-0! mt-1!">Cotações de {{ updatedAt | date: 'short' }}</p>
                }
            </div>
            <div class="flex flex-wrap gap-2">
                <a pButton routerLink="/carteira"><i class="pi pi-wallet" pButtonIcon aria-hidden="true"></i><span pButtonLabel>Gerir carteira</span></a>
            </div>
        </div>

        @if (failure(); as message) {
            <div class="card">
                <p-message severity="error" [text]="message" styleClass="mb-4" />
                <p-button label="Tentar novamente" icon="pi pi-refresh" [loading]="loading()" (onClick)="load()" />
            </div>
        } @else if (overview(); as data) {
            <div class="grid grid-cols-12 gap-8">
                <div class="col-span-12 md:col-span-6 2xl:col-span-3">
                    <app-stat-card label="Patrimônio total" icon="pi pi-wallet" hint="Posições a valor de mercado" [value]="data.marketValue" />
                </div>
                <div class="col-span-12 md:col-span-6 2xl:col-span-3">
                    <app-stat-card label="Total investido" icon="pi pi-money-bill" hint="Quantidade × preço médio" [value]="data.investedValue" />
                </div>
                <div class="col-span-12 md:col-span-6 2xl:col-span-3">
                    <div class="card mb-0! h-full">
                        <div class="flex justify-between gap-4 mb-4">
                            <div>
                                <div class="text-muted-color font-medium mb-4">Desempenho geral</div>
                                <div class="text-xl"><app-profit-loss [value]="data.profitLoss" [percent]="data.profitLossPercent" /></div>
                            </div>
                            <div class="flex items-center justify-center shrink-0 bg-primary-100 dark:bg-primary-400/10 rounded-border w-10 h-10">
                                <i class="pi pi-chart-line text-primary text-xl!" aria-hidden="true"></i>
                            </div>
                        </div>
                        <span class="text-muted-color">Lucro ou prejuízo das posições abertas</span>
                    </div>
                </div>
                <div class="col-span-12 md:col-span-6 2xl:col-span-3">
                    <div class="card mb-0! h-full">
                        <div class="flex justify-between gap-4 mb-4">
                            <div>
                                <div class="text-muted-color font-medium mb-4">Lucro realizado</div>
                                <div class="text-xl"><app-profit-loss [value]="data.realizedProfit" /></div>
                            </div>
                            <div class="flex items-center justify-center shrink-0 bg-primary-100 dark:bg-primary-400/10 rounded-border w-10 h-10">
                                <i class="pi pi-check-circle text-primary text-xl!" aria-hidden="true"></i>
                            </div>
                        </div>
                        <span class="text-muted-color">Resultado somado de todas as vendas</span>
                    </div>
                </div>

                @if (data.positionsWithoutQuote > 0) {
                    <div class="col-span-12">
                        <p-message severity="warn" icon="pi pi-exclamation-triangle" [text]="withoutQuoteMessage()" />
                    </div>
                }

                <section class="col-span-12" aria-label="Destaques de mercado">
                    <app-market-highlights [positions]="positions()" [market]="market()" />
                </section>

                @if (watchlist(); as items) {
                    <section class="col-span-12" aria-label="Alvos do plano">
                        <app-reached-targets [items]="items" />
                    </section>
                }

                <div class="col-span-12 flex flex-wrap items-center justify-between gap-4">
                    <div>
                        <h2 id="period-title" class="text-xl! font-semibold m-0!">Período de análise</h2>
                        <p class="text-surface-600 dark:text-surface-300 m-0! mt-1!">Vale para a tabela e para os gráficos abaixo.</p>
                    </div>
                    <p-selectbutton ariaLabelledBy="period-title" optionLabel="label" optionValue="value" [options]="periods" [unselectable]="true" [(ngModel)]="days" />
                </div>

                @if (marketFailed()) {
                    <div class="col-span-12">
                        <p-message severity="warn" icon="pi pi-exclamation-triangle" text="Não foi possível carregar os dados de mercado. O restante do painel continua atualizado." />
                    </div>
                }

                <div class="col-span-12 grid grid-cols-12 gap-8 transition-opacity" [class.opacity-60]="periodLoading()" [attr.aria-busy]="periodLoading()">
                    <section class="col-span-12" aria-labelledby="positions-title">
                        <div class="card mb-0!">
                            <h2 id="positions-title" class="text-xl! font-semibold mt-0! mb-6!">Posições atuais</h2>
                            <app-positions-table [positions]="positions()" [total]="data.marketValue" [market]="market()" [periodLabel]="periodLabel()" />
                        </div>
                    </section>

                    <section class="col-span-12 xl:col-span-8">
                        <app-asset-performance-chart [market]="market()" [positions]="positions()" [days]="days()" [periodLabel]="periodLabel()" />
                    </section>
                    <section class="col-span-12 xl:col-span-4">
                        <app-ranked-bars heading="Variação dos ativos" [description]="'Alta ou queda do preço em ' + periodLabel() + '.'" emptyMessage="Ainda não há preços suficientes neste período." [items]="changes()" [format]="formatPercent" [signed]="true" />
                    </section>

                    <section class="col-span-12">
                        <app-portfolio-radar-chart [axes]="radarAxes()" />
                    </section>

                    <section class="col-span-12 xl:col-span-8">
                        <app-history-chart [points]="history()" />
                    </section>
                    <section class="col-span-12 xl:col-span-4">
                        <app-return-chart [history]="history()" />
                    </section>
                </div>

                <section class="col-span-12 xl:col-span-4">
                    <app-ranked-bars heading="Composição por ativo" description="Peso de cada ativo no patrimônio." emptyMessage="Sua carteira ainda está vazia." [items]="assetShares()" [format]="formatShare" />
                </section>
                <section class="col-span-12 xl:col-span-4">
                    <app-ranked-bars heading="Resultado por ativo" description="Lucro ou prejuízo de cada posição aberta." emptyMessage="Nenhuma posição com cotação até agora." [items]="results()" [format]="formatMoney" [signed]="true" />
                </section>
                <section class="col-span-12 xl:col-span-4">
                    <app-allocation-chart [slices]="slices()" />
                </section>
                <section class="col-span-12">
                    <app-recent-transactions [transactions]="recent()" />
                </section>
            </div>
        } @else {
            <div class="grid grid-cols-12 gap-8" aria-busy="true" aria-label="Carregando a carteira">
                @for (card of [1, 2, 3]; track card) {
                    <div class="col-span-12 md:col-span-6 xl:col-span-4"><p-skeleton height="9rem" /></div>
                }
                <div class="col-span-12"><p-skeleton height="20rem" /></div>
            </div>
        }
    `
})
export class Dashboard {
    private readonly wallet = inject(WalletService);
    private readonly watchlistService = inject(WatchlistService);

    protected readonly overview = signal<DashboardOverview | null>(null);
    protected readonly positions = signal<DashboardPosition[]>([]);
    protected readonly recent = signal<Transaction[]>([]);
    protected readonly history = signal<PortfolioHistoryPoint[]>([]);
    protected readonly market = signal<MarketAsset[]>([]);
    /** Nulo sem resposta: é secundário, e o restante do painel continua útil sem ele. */
    protected readonly watchlist = signal<WatchlistItem[] | null>(null);
    protected readonly marketFailed = signal(false);
    protected readonly periods = PERIODS;
    protected readonly days = signal(30);
    protected readonly periodLoading = signal(false);
    protected readonly loading = signal(false);
    protected readonly failure = signal<string | null>(null);

    protected readonly periodLabel = computed(() => PERIODS.find((period) => period.value === this.days())?.label ?? '');
    protected readonly slices = computed(() => allocation(this.positions()));
    protected readonly changes = computed(() => periodChanges(this.market()));
    protected readonly assetShares = computed(() => composition(this.positions()));
    protected readonly results = computed(() => resultByAsset(this.positions()));
    protected readonly radarAxes = computed(() => portfolioRadar(this.positions(), this.market(), this.watchlist(), this.periodLabel()));

    protected readonly formatPercent = (value: number) => signed(value, `${decimal(value, 2)}%`);
    protected readonly formatShare = (value: number) => `${decimal(value, 1)}%`;
    protected readonly formatMoney = (value: number) => signed(value, `R$ ${decimal(value, 2)}`);

    protected readonly withoutQuoteMessage = computed(() => {
        const count = this.overview()?.positionsWithoutQuote ?? 0;
        return count === 1
            ? '1 posição ainda não tem cotação e está avaliada pelo preço médio, sem lucro ou prejuízo. A cotação é buscada logo após a compra e nas sincronizações automáticas.'
            : `${count} posições ainda não têm cotação e estão avaliadas pelo preço médio, sem lucro ou prejuízo. A cotação é buscada logo após a compra e nas sincronizações automáticas.`;
    });

    constructor() {
        this.load();
        // Só os alvos e o plano interessam aqui: o período mais curto traz menos pontos de preço.
        this.watchlistService.list(1).subscribe({ next: (items) => this.watchlist.set(items), error: () => undefined });
        // switchMap descarta a resposta do período anterior, que poderia chegar depois da do atual.
        toObservable(this.days)
            .pipe(
                tap(() => this.periodLoading.set(true)),
                switchMap((days) =>
                    forkJoin({
                        // Estes dados são secundários: sem eles, o restante do painel continua útil.
                        history: this.wallet.history(days).pipe(catchError(() => of<PortfolioHistoryPoint[]>([]))),
                        market: this.wallet.market(days).pipe(catchError(() => of(null)))
                    })
                ),
                takeUntilDestroyed()
            )
            .subscribe(({ history, market }) => {
                this.history.set(history);
                this.market.set(market ?? []);
                this.marketFailed.set(market === null);
                this.periodLoading.set(false);
            });
    }

    protected load(): void {
        this.loading.set(true);
        forkJoin({
            overview: this.wallet.overview(),
            positions: this.wallet.positions(),
            recent: this.wallet.recentTransactions(RECENT_SIZE)
        }).subscribe({
            next: ({ overview, positions, recent }) => {
                this.overview.set(overview);
                this.positions.set(positions);
                this.recent.set(recent);
                this.failure.set(null);
                this.loading.set(false);
            },
            error: (error: unknown) => {
                this.failure.set(errorMessage(error));
                this.loading.set(false);
            }
        });
    }
}
