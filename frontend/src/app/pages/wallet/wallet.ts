import { Component, computed, inject, signal } from '@angular/core';
import { catchError, forkJoin, of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { Asset, DashboardPosition, Position, Transaction, WatchlistItem } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';
import { TransactionReversalService } from '@/app/core/transaction-reversal.service';
import { WalletService } from '@/app/core/wallet.service';
import { WatchlistService } from '@/app/core/watchlist.service';
import { PositionsTable } from '@/app/shared/positions-table';
import { RecentTransactions } from '@/app/shared/recent-transactions';
import { WatchlistPlanDialog } from '@/app/pages/watchlist/components/watchlist-plan-dialog';
import { BuyDialog } from './components/buy-dialog';
import { SellDialog } from './components/sell-dialog';

const RECENT_SIZE = 10;

/** Onde as compras e vendas são lançadas. O acompanhamento fica no Painel. */
@Component({
    selector: 'app-wallet',
    imports: [BuyDialog, ButtonModule, MessageModule, PositionsTable, RecentTransactions, SellDialog, SkeletonModule, WatchlistPlanDialog],
    template: `
        <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
            <div>
                <h1 class="text-3xl! font-semibold m-0!">Carteira</h1>
                <p class="text-surface-600 dark:text-surface-300 m-0! mt-1!">Registre as compras e vendas dos seus ativos.</p>
            </div>
            <div class="flex flex-wrap gap-2">
                <p-button label="Registrar venda" icon="pi pi-minus" severity="secondary" [outlined]="true" [disabled]="positions().length === 0" (onClick)="openSell(null)" />
                <p-button label="Registrar compra" icon="pi pi-plus" [disabled]="!loaded()" (onClick)="openBuy(null)" />
            </div>
        </div>

        @if (failure(); as message) {
            <div class="card">
                <p-message severity="error" [text]="message" styleClass="mb-4" />
                <p-button label="Tentar novamente" icon="pi pi-refresh" [loading]="loading()" (onClick)="load()" />
            </div>
        } @else if (loaded()) {
            <div class="flex flex-col gap-8">
                <section aria-labelledby="wallet-positions-title">
                    <div class="card mb-0!">
                        <h2 id="wallet-positions-title" class="text-xl! font-semibold mt-0! mb-6!">Posições</h2>
                        <app-positions-table [manageable]="true" [positions]="positions()" [total]="total()" [plans]="plans()" (buy)="openBuy($event)" (sell)="openSell($event)" (plan)="openPlan($event)" />
                    </div>
                </section>
                <section>
                    <app-recent-transactions [manageable]="true" [transactions]="recent()" (reverse)="reverse($event)" />
                </section>
            </div>
        } @else {
            <p-skeleton height="20rem" />
        }

        <app-buy-dialog [position]="selected()" [(visible)]="buyVisible" (completed)="onBuyCompleted($event)" (closed)="restoreFocus()" />
        <app-sell-dialog [position]="selected()" [positions]="positions()" [(visible)]="sellVisible" (completed)="onSellCompleted($event)" (closed)="restoreFocus()" />
        <app-watchlist-plan-dialog [item]="planItem()" [asset]="planAsset()" [(visible)]="planVisible" (saved)="onPlanSaved($event)" (closed)="restoreFocus()" />
    `
})
export class Wallet {
    private readonly wallet = inject(WalletService);
    private readonly watchlist = inject(WatchlistService);
    private readonly messages = inject(MessageService);
    private readonly reversal = inject(TransactionReversalService);

    /** Elemento que abriu o diálogo, para devolver o foco a ele quando o diálogo fechar. */
    private returnFocusTo: HTMLElement | null = null;

    protected readonly positions = signal<DashboardPosition[]>([]);
    protected readonly recent = signal<Transaction[]>([]);
    protected readonly loaded = signal(false);
    protected readonly loading = signal(false);
    protected readonly failure = signal<string | null>(null);

    protected readonly buyVisible = signal(false);
    protected readonly sellVisible = signal(false);
    protected readonly selected = signal<Position | null>(null);
    protected readonly plans = signal<WatchlistItem[]>([]);
    protected readonly planVisible = signal(false);
    protected readonly planItem = signal<WatchlistItem | null>(null);
    protected readonly planAsset = signal<(Asset & { price: number | null; averagePrice: number | null }) | null>(null);

    protected readonly total = computed(() => this.positions().reduce((sum, position) => sum + position.marketValue, 0));

    constructor() {
        this.load();
    }

    protected load(): void {
        this.loading.set(true);
        forkJoin({
            positions: this.wallet.positions(),
            recent: this.wallet.recentTransactions(RECENT_SIZE),
            // Secundário: sem os planos, a carteira continua funcionando.
            plans: this.watchlist.list(1).pipe(catchError(() => of<WatchlistItem[]>([])))
        }).subscribe({
            next: ({ positions, recent, plans }) => {
                this.plans.set(plans);
                this.positions.set(positions);
                this.recent.set(recent);
                this.loaded.set(true);
                this.failure.set(null);
                this.loading.set(false);
            },
            error: (error: unknown) => {
                this.failure.set(errorMessage(error));
                this.loading.set(false);
            }
        });
    }

    protected openBuy(position: Position | null): void {
        this.rememberFocus();
        this.selected.set(position);
        this.buyVisible.set(true);
    }

    protected openSell(position: Position | null): void {
        this.rememberFocus();
        this.selected.set(position);
        this.sellVisible.set(true);
    }

    /** Definir um plano aqui também coloca o ativo no Acompanhamento. */
    protected openPlan(position: DashboardPosition): void {
        this.rememberFocus();
        const { symbol, name, assetType, currentPrice, averagePrice } = position;
        const item = this.plans().find((plan) => plan.symbol === symbol) ?? null;
        this.planItem.set(item);
        this.planAsset.set(item ? null : { symbol, name, assetType, price: currentPrice, averagePrice });
        this.planVisible.set(true);
    }

    protected onPlanSaved(item: WatchlistItem): void {
        this.notify(`Plano de ${item.symbol} salvo`);
    }

    protected restoreFocus(): void {
        this.returnFocusTo?.focus();
        this.returnFocusTo = null;
    }

    protected reverse(transaction: Transaction): void {
        this.reversal.reverse(transaction, () => this.load());
    }

    protected onBuyCompleted(transaction: Transaction): void {
        this.notify(`Compra de ${transaction.symbol} registrada`);
    }

    protected onSellCompleted(transaction: Transaction): void {
        this.notify(`Venda de ${transaction.symbol} registrada`);
    }

    private notify(summary: string): void {
        this.messages.add({ severity: 'success', closable: false, summary });
        this.load();
    }

    private rememberFocus(): void {
        this.returnFocusTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    }
}
