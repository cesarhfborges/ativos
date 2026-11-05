import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { FiatMovementType, OperationResponse, Position, WalletSummary } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';
import { allocation, investedTotal, roundMoney } from '@/app/core/portfolio';
import { WalletService } from '@/app/core/wallet.service';
import { AllocationChart } from './components/allocation-chart';
import { BuyDialog } from './components/buy-dialog';
import { FiatDialog } from './components/fiat-dialog';
import { PositionsTable } from './components/positions-table';
import { SellDialog } from './components/sell-dialog';
import { StatCard } from './components/stat-card';

@Component({
    selector: 'app-dashboard',
    imports: [AllocationChart, BuyDialog, ButtonModule, FiatDialog, MessageModule, PositionsTable, SellDialog, SkeletonModule, StatCard],
    template: `
        <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
            <h1 class="text-3xl! font-semibold m-0!">Painel</h1>
            <div class="flex flex-wrap gap-2">
                <p-button label="Aportar" icon="pi pi-arrow-down" severity="secondary" [outlined]="true" [disabled]="!summary()" (onClick)="openFiat('DEPOSIT')" />
                <p-button label="Resgatar" icon="pi pi-arrow-up" severity="secondary" [outlined]="true" [disabled]="!summary()" (onClick)="openFiat('WITHDRAW')" />
                <p-button label="Registrar compra" icon="pi pi-plus" [disabled]="!summary()" (onClick)="openBuy(null)" />
            </div>
        </div>

        @if (failure(); as message) {
            <div class="card">
                <p-message severity="error" [text]="message" styleClass="mb-4" />
                <p-button label="Tentar novamente" icon="pi pi-refresh" [loading]="loading()" (onClick)="load()" />
            </div>
        } @else if (summary(); as wallet) {
            <div class="grid grid-cols-12 gap-8">
                <div class="col-span-12 md:col-span-6 xl:col-span-4">
                    <app-stat-card label="Patrimônio a custo" icon="pi pi-wallet" hint="Caixa + posições a preço médio" [value]="patrimony()" />
                </div>
                <div class="col-span-12 md:col-span-6 xl:col-span-4">
                    <app-stat-card label="Saldo em caixa" icon="pi pi-money-bill" hint="Disponível para compras e resgates" [value]="wallet.fiatBalance" />
                </div>
                <div class="col-span-12 md:col-span-6 xl:col-span-4">
                    <app-stat-card label="Total investido" icon="pi pi-chart-line" hint="Quantidade × preço médio" [value]="invested()" />
                </div>

                <div class="col-span-12">
                    <p-message severity="info" icon="pi pi-info-circle" text="Rentabilidade e valor de mercado aparecerão aqui quando o módulo de cotações estiver disponível. Por enquanto, os valores usam o preço médio." />
                </div>

                <section class="col-span-12" aria-labelledby="positions-title">
                    <div class="card mb-0">
                        <h2 id="positions-title" class="text-xl! font-semibold mt-0! mb-6!">Posições atuais</h2>
                        <app-positions-table [positions]="wallet.positions" (buy)="openBuy($event)" (sell)="openSell($event)" />
                    </div>
                </section>
                <section class="col-span-12">
                    <app-allocation-chart [slices]="slices()" />
                </section>
            </div>

            <app-fiat-dialog [type]="fiatType()" [balance]="wallet.fiatBalance" [(visible)]="fiatVisible" (completed)="onFiatCompleted($event)" (closed)="restoreFocus()" />
            <app-buy-dialog [balance]="wallet.fiatBalance" [position]="selected()" [(visible)]="buyVisible" (completed)="onBuyCompleted($event)" (closed)="restoreFocus()" />
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
export class Dashboard implements OnInit {
    private readonly wallet = inject(WalletService);
    private readonly messages = inject(MessageService);

    /** Elemento que abriu o diálogo, para devolver o foco a ele quando o diálogo fechar. */
    private returnFocusTo: HTMLElement | null = null;

    protected readonly summary = signal<WalletSummary | null>(null);
    protected readonly loading = signal(false);
    protected readonly failure = signal<string | null>(null);

    protected readonly fiatVisible = signal(false);
    protected readonly fiatType = signal<FiatMovementType>('DEPOSIT');
    protected readonly buyVisible = signal(false);
    protected readonly sellVisible = signal(false);
    protected readonly selected = signal<Position | null>(null);

    protected readonly invested = computed(() => investedTotal(this.summary()?.positions ?? []));
    protected readonly patrimony = computed(() => roundMoney((this.summary()?.fiatBalance ?? 0) + this.invested()));
    protected readonly slices = computed(() => allocation(this.summary() ?? { fiatBalance: 0, positions: [] }));

    ngOnInit(): void {
        this.load();
    }

    protected load(): void {
        this.loading.set(true);
        this.wallet.summary().subscribe({
            next: (summary) => {
                this.summary.set(summary);
                this.failure.set(null);
                this.loading.set(false);
            },
            error: (error: unknown) => {
                this.failure.set(errorMessage(error));
                this.loading.set(false);
            }
        });
    }

    protected openFiat(type: FiatMovementType): void {
        this.rememberFocus();
        this.fiatType.set(type);
        this.fiatVisible.set(true);
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

    protected onFiatCompleted(type: FiatMovementType): void {
        this.notify(type === 'DEPOSIT' ? 'Aporte registrado' : 'Resgate registrado');
    }

    protected onBuyCompleted(response: OperationResponse): void {
        const detail = response.autoDeposit ? 'A diferença entrou no caixa como um aporte automático.' : undefined;
        this.notify(`Compra de ${response.transaction.symbol} registrada`, detail);
    }

    protected onSellCompleted(response: OperationResponse): void {
        this.notify(`Venda de ${response.transaction.symbol} registrada`);
    }

    private notify(summary: string, detail?: string): void {
        this.messages.add({ severity: 'success', closable: false, summary, detail });
        this.load();
    }

    private rememberFocus(): void {
        this.returnFocusTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    }
}
