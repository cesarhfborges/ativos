import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { DashboardOverview, DashboardPosition, PortfolioHistoryPoint, Position, Transaction } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';
import { allocation } from '@/app/core/portfolio';
import { WalletService } from '@/app/core/wallet.service';
import { AllocationChart } from './components/allocation-chart';
import { BuyDialog } from './components/buy-dialog';
import { HistoryChart } from './components/history-chart';
import { PositionsTable } from './components/positions-table';
import { ProfitLoss } from './components/profit-loss';
import { RecentTransactions } from './components/recent-transactions';
import { SellDialog } from './components/sell-dialog';
import { StatCard } from './components/stat-card';

const RECENT_SIZE = 5;

@Component({
    selector: 'app-dashboard',
    imports: [AllocationChart, BuyDialog, ButtonModule, DatePipe, HistoryChart, MessageModule, PositionsTable, ProfitLoss, RecentTransactions, SellDialog, SkeletonModule, StatCard],
    template: `
        <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
            <div>
                <h1 class="text-3xl! font-semibold m-0!">Painel</h1>
                @if (overview()?.quotesUpdatedAt; as updatedAt) {
                    <p class="text-surface-600 dark:text-surface-300 m-0! mt-1!">Cotações de {{ updatedAt | date: 'short' }}</p>
                }
            </div>
            <div class="flex flex-wrap gap-2">
                <p-button label="Atualizar cotações" icon="pi pi-refresh" severity="secondary" [text]="true" [disabled]="!overview()" [loading]="refreshing()" (onClick)="refreshQuotes()" />
                <p-button label="Registrar compra" icon="pi pi-plus" [disabled]="!overview()" (onClick)="openBuy(null)" />
            </div>
        </div>

        @if (failure(); as message) {
            <div class="card">
                <p-message severity="error" [text]="message" styleClass="mb-4" />
                <p-button label="Tentar novamente" icon="pi pi-refresh" [loading]="loading()" (onClick)="load()" />
            </div>
        } @else if (overview(); as data) {
            <div class="grid grid-cols-12 gap-8">
                <div class="col-span-12 md:col-span-6 xl:col-span-4">
                    <app-stat-card label="Patrimônio total" icon="pi pi-wallet" hint="Posições a valor de mercado" [value]="data.marketValue" />
                </div>
                <div class="col-span-12 md:col-span-6 xl:col-span-4">
                    <app-stat-card label="Total investido" icon="pi pi-money-bill" hint="Quantidade × preço médio" [value]="data.investedValue" />
                </div>
                <div class="col-span-12 xl:col-span-4">
                    <div class="card mb-0 h-full">
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

                @if (data.positionsWithoutQuote > 0) {
                    <div class="col-span-12">
                        <p-message severity="warn" icon="pi pi-exclamation-triangle" [text]="withoutQuoteMessage()" />
                    </div>
                }

                <section class="col-span-12" aria-labelledby="positions-title">
                    <div class="card mb-0">
                        <h2 id="positions-title" class="text-xl! font-semibold mt-0! mb-6!">Posições atuais</h2>
                        <app-positions-table [positions]="positions()" [total]="data.marketValue" (buy)="openBuy($event)" (sell)="openSell($event)" />
                    </div>
                </section>
                <section class="col-span-12">
                    <app-history-chart [points]="history()" [(days)]="historyDays" />
                </section>
                <section class="col-span-12 xl:col-span-7">
                    <app-allocation-chart [slices]="slices()" />
                </section>
                <section class="col-span-12 xl:col-span-5">
                    <app-recent-transactions [transactions]="recent()" />
                </section>
            </div>
            <app-buy-dialog [position]="selected()" [(visible)]="buyVisible" (completed)="onBuyCompleted($event)" (closed)="restoreFocus()" />
            <app-sell-dialog [position]="selected()" [(visible)]="sellVisible" (completed)="onSellCompleted($event)" (closed)="restoreFocus()" />
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
    private readonly messages = inject(MessageService);

    /** Elemento que abriu o diálogo, para devolver o foco a ele quando o diálogo fechar. */
    private returnFocusTo: HTMLElement | null = null;

    protected readonly overview = signal<DashboardOverview | null>(null);
    protected readonly positions = signal<DashboardPosition[]>([]);
    protected readonly recent = signal<Transaction[]>([]);
    protected readonly history = signal<PortfolioHistoryPoint[]>([]);
    protected readonly historyDays = signal(30);
    protected readonly loading = signal(false);
    protected readonly refreshing = signal(false);
    protected readonly failure = signal<string | null>(null);

    protected readonly buyVisible = signal(false);
    protected readonly sellVisible = signal(false);
    protected readonly selected = signal<Position | null>(null);

    protected readonly slices = computed(() => allocation(this.positions()));
    protected readonly withoutQuoteMessage = computed(() => {
        const count = this.overview()?.positionsWithoutQuote ?? 0;
        return count === 1
            ? '1 posição ainda não tem cotação e está avaliada pelo preço médio, sem lucro ou prejuízo. Use "Atualizar cotações" para buscar agora.'
            : `${count} posições ainda não têm cotação e estão avaliadas pelo preço médio, sem lucro ou prejuízo. Use "Atualizar cotações" para buscar agora.`;
    });

    constructor() {
        this.load();
        effect(() => this.loadHistory(this.historyDays()));
    }

    protected load(): void {
        this.loading.set(true);
        forkJoin({
            overview: this.wallet.overview(),
            positions: this.wallet.positions(),
            recent: this.wallet.statement(0, RECENT_SIZE)
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

    protected refreshQuotes(): void {
        this.refreshing.set(true);
        this.wallet.refreshQuotes().subscribe({
            next: ({ refreshed }) => {
                this.refreshing.set(false);
                this.messages.add(
                    refreshed
                        ? { severity: 'success', closable: false, summary: 'Cotações atualizadas' }
                        : { severity: 'info', closable: false, summary: 'Cotações já atualizadas', detail: 'A última coleta foi há menos de um minuto. Para buscar um ativo recém-comprado, tente de novo em instantes.' }
                );
                this.load();
            },
            error: (error: unknown) => {
                this.refreshing.set(false);
                this.messages.add({ severity: 'error', closable: false, summary: 'Não foi possível atualizar', detail: errorMessage(error) });
            }
        });
    }

    protected openBuy(position: Position | null): void {
        this.rememberFocus();
        this.selected.set(position);
        this.buyVisible.set(true);
    }

    protected openSell(position: Position): void {
        this.rememberFocus();
        this.selected.set(position);
        this.sellVisible.set(true);
    }

    protected restoreFocus(): void {
        this.returnFocusTo?.focus();
        this.returnFocusTo = null;
    }

    protected onBuyCompleted(transaction: Transaction): void {
        this.notify(`Compra de ${transaction.symbol} registrada`);
    }

    protected onSellCompleted(transaction: Transaction): void {
        this.notify(`Venda de ${transaction.symbol} registrada`);
    }

    private loadHistory(days: number): void {
        this.wallet.history(days).subscribe({
            next: (history) => this.history.set(history),
            // O gráfico é secundário: sem histórico, o restante do painel continua útil.
            error: () => this.history.set([])
        });
    }

    private notify(summary: string): void {
        this.messages.add({ severity: 'success', closable: false, summary });
        this.load();
    }

    private rememberFocus(): void {
        this.returnFocusTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    }
}
