import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, EMPTY, skip, Subject, switchMap, tap } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { TableModule } from 'primeng/table';
import { SimplePage, Transaction, TransactionType } from '@/app/core/api.models';
import { errorMessage } from '@/app/core/api-error';
import { defaultStatementFilter, hasActiveFilter, isValidFilter, StatementFilter } from '@/app/core/statement-filter';
import { TRANSACTION_DISPLAY } from '@/app/core/transaction-display';
import { TransactionReversalService } from '@/app/core/transaction-reversal.service';
import { WalletService } from '@/app/core/wallet.service';
import { ProfitLoss } from '@/app/shared/profit-loss';
import { StatementFilters } from './components/statement-filters';

const PAGE_SIZE = 20;
const FILTER_DELAY_MS = 300;

@Component({
    selector: 'app-statement',
    imports: [ButtonModule, CurrencyPipe, DatePipe, DecimalPipe, MessageModule, ProfitLoss, StatementFilters, TableModule],
    template: `
        <h1 class="text-3xl! font-semibold mt-0! mb-6!">Extrato</h1>

        <app-statement-filters [(filter)]="filter" />

        <div class="card">
            @if (failure(); as message) {
                <p-message severity="error" [text]="message" styleClass="mb-4" />
                <p-button label="Tentar novamente" icon="pi pi-refresh" [loading]="loading()" (onClick)="load(currentPage())" />
            } @else {
                <div class="stack-table stack-66">
                    <p-table [value]="result()?.data ?? []" dataKey="id" [loading]="loading()">
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
                                <td class="whitespace-nowrap" data-label="Data">{{ transaction.timestamp | date: 'short' }}</td>
                                <td class="whitespace-nowrap" data-label="Operação"><i class="mr-2 {{ display(transaction.type).icon }}" aria-hidden="true"></i>{{ display(transaction.type).label }}</td>
                                <td data-label="Ativo">{{ transaction.symbol }}</td>
                                <td class="text-right" data-label="Quantidade">
                                    {{ transaction.quantity | number: '1.0-8' }}
                                </td>
                                <td class="text-right" data-label="Preço unitário">
                                    {{ transaction.unitPrice | currency: 'BRL' : 'symbol' : '1.2-8' }}
                                </td>
                                <td class="text-right" data-label="Taxa">{{ transaction.fee | currency: 'BRL' : 'symbol' : '1.2-4' }}</td>
                                <td class="text-right font-medium whitespace-nowrap" data-label="Total" [class.line-through]="transaction.reversedAt">{{ transaction.totalAmount | currency: 'BRL' : 'symbol' : '1.2-4' }}</td>
                                <td class="text-right whitespace-nowrap" data-label="Lucro realizado">
                                    @if (transaction.realizedProfit !== null) {
                                        <app-profit-loss [class.line-through]="transaction.reversedAt" [value]="transaction.realizedProfit" />
                                    } @else {
                                        —
                                    }
                                </td>
                                <td class="text-right whitespace-nowrap" data-label="Situação">
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
                                <td colspan="9" class="text-center text-muted-color py-8">{{ emptyMessage() }}</td>
                            </tr>
                        </ng-template>
                    </p-table>
                </div>

                <nav class="flex items-center justify-end gap-4 mt-4" aria-label="Paginação do extrato">
                    <p-button label="Anterior" icon="pi pi-angle-left" severity="secondary" [text]="true" [disabled]="!result()?.prev_page_url || loading()" (onClick)="load(currentPage() - 1)" />
                    <span aria-live="polite">{{ pageLabel() }}</span>
                    <p-button label="Próxima" icon="pi pi-angle-right" iconPos="right" severity="secondary" [text]="true" [disabled]="!result()?.next_page_url || loading()" (onClick)="load(currentPage() + 1)" />
                </nav>
            }
        </div>
    `
})
export class Statement {
    private readonly wallet = inject(WalletService);
    private readonly reversal = inject(TransactionReversalService);

    protected readonly filter = signal<StatementFilter>(defaultStatementFilter(new Date()));
    protected readonly result = signal<SimplePage<Transaction> | null>(null);
    protected readonly loading = signal(true);
    protected readonly failure = signal<string | null>(null);

    protected readonly currentPage = computed(() => this.result()?.current_page ?? 1);

    protected readonly pageLabel = computed(() => {
        const page = this.result();
        return page?.from ? `Página ${page.current_page} · ${page.from} a ${page.to}` : `Página ${this.currentPage()}`;
    });

    protected readonly emptyMessage = computed(() => (hasActiveFilter(this.filter()) ? 'Nenhum lançamento para esses filtros.' : 'Nenhum lançamento registrado ainda.'));

    private readonly requests = new Subject<number>();

    constructor() {
        // switchMap descarta a resposta de um filtro anterior, que poderia chegar depois da do atual.
        this.requests
            .pipe(
                tap(() => this.loading.set(true)),
                switchMap((page) =>
                    this.wallet.statement(this.filter(), page, PAGE_SIZE).pipe(
                        catchError((error: unknown) => {
                            this.failure.set(errorMessage(error));
                            this.loading.set(false);
                            return EMPTY;
                        })
                    )
                ),
                takeUntilDestroyed()
            )
            .subscribe((page) => this.show(page));

        // A espera junta os ajustes seguidos, como digitar um valor, em uma só requisição.
        toObservable(this.filter)
            .pipe(skip(1), debounceTime(FILTER_DELAY_MS), takeUntilDestroyed())
            .subscribe(() => this.load(1));

        this.load(1);
    }

    protected load(page: number): void {
        if (isValidFilter(this.filter())) {
            this.requests.next(page);
        }
    }

    protected reverse(transaction: Transaction): void {
        this.reversal.reverse(transaction, () => this.load(this.currentPage()));
    }

    protected display(type: TransactionType) {
        return TRANSACTION_DISPLAY[type];
    }

    /** Filtrando por "Válidas", estornar o único lançamento da última página a deixa vazia. */
    private show(page: SimplePage<Transaction>): void {
        if (page.data.length === 0 && page.current_page > 1) {
            this.load(page.current_page - 1);
            return;
        }
        this.result.set(page);
        this.failure.set(null);
        this.loading.set(false);
    }
}
