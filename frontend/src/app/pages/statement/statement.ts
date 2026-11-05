import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { TableModule } from 'primeng/table';
import { Transaction, TransactionType } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';
import { WalletService } from '@/app/core/wallet.service';

const PAGE_SIZE = 20;

const TYPE_DISPLAY: Record<TransactionType, { label: string; icon: string; credit: boolean }> = {
    BUY: { label: 'Compra', icon: 'pi pi-shopping-cart', credit: false },
    SELL: { label: 'Venda', icon: 'pi pi-dollar', credit: true },
    DEPOSIT: { label: 'Aporte', icon: 'pi pi-arrow-down', credit: true },
    WITHDRAW: { label: 'Resgate', icon: 'pi pi-arrow-up', credit: false }
};

@Component({
    selector: 'app-statement',
    imports: [ButtonModule, CurrencyPipe, DatePipe, DecimalPipe, MessageModule, TableModule],
    template: `
        <h1 class="text-3xl! font-semibold mt-0! mb-6!">Extrato</h1>

        <div class="card">
            @if (failure(); as message) {
                <p-message severity="error" [text]="message" styleClass="mb-4" />
                <p-button label="Tentar novamente" icon="pi pi-refresh" [loading]="loading()" (onClick)="load(page())" />
            } @else {
                <div class="overflow-x-auto" tabindex="0" role="region" aria-label="Lançamentos do extrato">
                    <p-table class="block min-w-[52rem]" [value]="transactions()" dataKey="id" [loading]="loading()">
                        <ng-template #header>
                            <tr>
                                <th scope="col">Data</th>
                                <th scope="col">Lançamento</th>
                                <th scope="col">Ativo</th>
                                <th scope="col" class="text-right!">Quantidade</th>
                                <th scope="col" class="text-right!">Preço unitário</th>
                                <th scope="col" class="text-right!">Taxa</th>
                                <th scope="col" class="text-right!">Total no caixa</th>
                            </tr>
                        </ng-template>
                        <ng-template #body let-transaction>
                            <tr>
                                <td class="whitespace-nowrap">{{ transaction.timestamp | date: 'short' }}</td>
                                <td class="whitespace-nowrap"><i class="mr-2 {{ display(transaction.type).icon }}" aria-hidden="true"></i>{{ display(transaction.type).label }}</td>
                                <td>{{ transaction.symbol ?? '—' }}</td>
                                <td class="text-right">
                                    @if (transaction.quantity !== null) {
                                        {{ transaction.quantity | number: '1.0-8' }}
                                    } @else {
                                        —
                                    }
                                </td>
                                <td class="text-right">
                                    @if (transaction.unitPrice !== null) {
                                        {{ transaction.unitPrice | currency: 'BRL' : 'symbol' : '1.2-4' }}
                                    } @else {
                                        —
                                    }
                                </td>
                                <td class="text-right">{{ transaction.fee | currency: 'BRL' : 'symbol' : '1.2-4' }}</td>
                                <td class="text-right font-medium whitespace-nowrap">{{ display(transaction.type).credit ? '+' : '−' }} {{ transaction.totalAmount | currency: 'BRL' : 'symbol' : '1.2-4' }}</td>
                            </tr>
                        </ng-template>
                        <ng-template #emptymessage>
                            <tr>
                                <td colspan="7" class="text-center text-muted-color py-8">{{ page() === 0 ? 'Nenhum lançamento registrado ainda.' : 'Não há mais lançamentos.' }}</td>
                            </tr>
                        </ng-template>
                    </p-table>
                </div>

                <nav class="flex items-center justify-end gap-4 mt-4" aria-label="Paginação do extrato">
                    <p-button label="Anterior" icon="pi pi-angle-left" severity="secondary" [text]="true" [disabled]="page() === 0 || loading()" (onClick)="load(page() - 1)" />
                    <span aria-live="polite">Página {{ page() + 1 }}</span>
                    <p-button label="Próxima" icon="pi pi-angle-right" iconPos="right" severity="secondary" [text]="true" [disabled]="!hasNext() || loading()" (onClick)="load(page() + 1)" />
                </nav>
            }
        </div>
    `
})
export class Statement implements OnInit {
    private readonly wallet = inject(WalletService);

    protected readonly transactions = signal<Transaction[]>([]);
    protected readonly page = signal(0);
    protected readonly loading = signal(true);
    protected readonly failure = signal<string | null>(null);

    /** A API não informa o total: uma página cheia indica que pode haver mais lançamentos. */
    protected readonly hasNext = computed(() => this.transactions().length === PAGE_SIZE);

    ngOnInit(): void {
        this.load(0);
    }

    protected load(page: number): void {
        this.loading.set(true);
        this.wallet.statement(page, PAGE_SIZE).subscribe({
            next: (transactions) => {
                this.transactions.set(transactions);
                this.page.set(page);
                this.failure.set(null);
                this.loading.set(false);
            },
            error: (error: unknown) => {
                this.failure.set(errorMessage(error));
                this.loading.set(false);
            }
        });
    }

    protected display(type: TransactionType) {
        return TYPE_DISPLAY[type];
    }
}
