import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { Transaction, TransactionType } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';
import { TRANSACTION_DISPLAY } from '@/app/core/transaction-display';
import { TransactionReversalService } from '@/app/core/transaction-reversal.service';
import { WalletService } from '@/app/core/wallet.service';
import { ProfitLoss } from '@/app/shared/profit-loss';

const PAGE_SIZE = 20;

@Component({
    selector: 'app-statement',
    imports: [FormsModule, ButtonModule, CurrencyPipe, DatePipe, DecimalPipe, MessageModule, ProfitLoss, SelectModule, TableModule],
    template: `
        <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
            <h1 class="text-3xl! font-semibold m-0!">Extrato</h1>
            <div class="flex items-center gap-2">
                <span id="statement-filter-label" class="font-medium">Tipo</span>
                <p-select ariaLabelledBy="statement-filter-label" optionLabel="label" optionValue="value" class="w-44" [options]="filterOptions" [ngModel]="filter()" (ngModelChange)="applyFilter($event)" />
            </div>
        </div>

        <div class="card">
            @if (failure(); as message) {
                <p-message severity="error" [text]="message" styleClass="mb-4" />
                <p-button label="Tentar novamente" icon="pi pi-refresh" [loading]="loading()" (onClick)="load(page())" />
            } @else {
                <div class="overflow-x-auto" tabindex="0" role="region" aria-label="Lançamentos do extrato">
                    <p-table class="block min-w-[60rem]" [value]="transactions()" dataKey="id" [loading]="loading()">
                        <ng-template #header>
                            <tr>
                                <th scope="col">Data</th>
                                <th scope="col">Operação</th>
                                <th scope="col">Ativo</th>
                                <th scope="col" class="text-right!">Quantidade</th>
                                <th scope="col" class="text-right!">Preço unitário</th>
                                <th scope="col" class="text-right!">Taxa</th>
                                <th scope="col" class="text-right!">Total</th>
                                <th scope="col" class="text-right!">Lucro realizado</th>
                                <th scope="col" class="text-right!">Situação</th>
                            </tr>
                        </ng-template>
                        <ng-template #body let-transaction>
                            <tr [class.text-muted-color]="transaction.reversedAt">
                                <td class="whitespace-nowrap">{{ transaction.timestamp | date: 'short' }}</td>
                                <td class="whitespace-nowrap"><i class="mr-2 {{ display(transaction.type).icon }}" aria-hidden="true"></i>{{ display(transaction.type).label }}</td>
                                <td>{{ transaction.symbol }}</td>
                                <td class="text-right">
                                    {{ transaction.quantity | number: '1.0-8' }}
                                </td>
                                <td class="text-right">
                                    {{ transaction.unitPrice | currency: 'BRL' : 'symbol' : '1.2-4' }}
                                </td>
                                <td class="text-right">{{ transaction.fee | currency: 'BRL' : 'symbol' : '1.2-4' }}</td>
                                <td class="text-right font-medium whitespace-nowrap" [class.line-through]="transaction.reversedAt">{{ transaction.totalAmount | currency: 'BRL' : 'symbol' : '1.2-4' }}</td>
                                <td class="text-right whitespace-nowrap">
                                    @if (transaction.realizedProfit !== null) {
                                        <app-profit-loss [class.line-through]="transaction.reversedAt" [value]="transaction.realizedProfit" />
                                    } @else {
                                        —
                                    }
                                </td>
                                <td class="text-right whitespace-nowrap">
                                    @if (transaction.reversedAt) {
                                        <div class="font-medium"><i class="pi pi-undo mr-1" aria-hidden="true"></i>Estornada</div>
                                        <div class="text-muted-color text-sm">{{ transaction.reversedAt | date: 'short' }}</div>
                                    } @else {
                                        <p-button
                                            label="Estornar"
                                            size="small"
                                            severity="secondary"
                                            [text]="true"
                                            [ariaLabel]="'Estornar ' + display(transaction.type).label.toLowerCase() + ' de ' + transaction.symbol"
                                            (onClick)="reverse(transaction)"
                                        />
                                    }
                                </td>
                            </tr>
                        </ng-template>
                        <ng-template #emptymessage>
                            <tr>
                                <td colspan="9" class="text-center text-muted-color py-8">{{ page() === 0 ? 'Nenhum lançamento registrado ainda.' : 'Não há mais lançamentos.' }}</td>
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
    private readonly reversal = inject(TransactionReversalService);

    protected readonly filterOptions: { label: string; value: TransactionType | null }[] = [
        { label: 'Todos', value: null },
        ...(Object.keys(TRANSACTION_DISPLAY) as TransactionType[]).map((value) => ({ label: TRANSACTION_DISPLAY[value].label, value }))
    ];
    protected readonly filter = signal<TransactionType | null>(null);
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
        this.wallet.statement(page, PAGE_SIZE, this.filter()).subscribe({
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

    protected reverse(transaction: Transaction): void {
        this.reversal.reverse(transaction, () => this.load(this.page()));
    }

    protected applyFilter(type: TransactionType | null): void {
        this.filter.set(type);
        this.load(0);
    }

    protected display(type: TransactionType) {
        return TRANSACTION_DISPLAY[type];
    }
}
