import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { Transaction } from '@/app/core/api.models';
import { TRANSACTION_DISPLAY } from '@/app/core/transaction-display';

@Component({
    selector: 'app-recent-transactions',
    imports: [ButtonModule, CurrencyPipe, DatePipe, RouterLink],
    template: `<div class="card mb-0! h-full">
        <div class="flex items-center justify-between gap-4 mb-4">
            <h2 class="text-xl! font-semibold m-0!">Últimas operações</h2>
            <a routerLink="/extrato" class="text-primary font-medium underline">Ver extrato</a>
        </div>

        @if (transactions().length === 0) {
            <p class="text-muted-color m-0!">Nenhuma operação registrada ainda.</p>
        } @else {
            <ul class="list-none m-0 p-0">
                @for (transaction of transactions(); track transaction.id) {
                    <li class="flex items-center justify-between gap-4 py-3 border-b border-surface-200 dark:border-surface-700 last:border-b-0">
                        <div class="flex items-center gap-3 min-w-0">
                            <i class="text-muted-color {{ display[transaction.type].icon }}" aria-hidden="true"></i>
                            <div class="min-w-0">
                                <div class="font-medium truncate" [class.line-through]="transaction.reversedAt">
                                    {{ display[transaction.type].label }}
                                    {{ transaction.symbol }}
                                </div>
                                <div class="text-muted-color text-sm">
                                    {{ transaction.timestamp | date: 'short' }}
                                    @if (transaction.reversedAt) {
                                        · Estornada
                                    }
                                </div>
                            </div>
                        </div>
                        <div class="flex items-center gap-2 shrink-0">
                            <div class="font-medium whitespace-nowrap" [class.line-through]="transaction.reversedAt">{{ transaction.totalAmount | currency: 'BRL' : 'symbol' : '1.2-2' }}</div>
                            @if (manageable() && !transaction.reversedAt) {
                                <p-button
                                    label="Estornar"
                                    size="small"
                                    severity="secondary"
                                    [text]="true"
                                    [ariaLabel]="'Estornar ' + display[transaction.type].label.toLowerCase() + ' de ' + transaction.symbol"
                                    (onClick)="reverse.emit(transaction)"
                                />
                            }
                        </div>
                    </li>
                }
            </ul>
        }
    </div>`
})
export class RecentTransactions {
    readonly transactions = input.required<Transaction[]>();
    /** Com a ação de estornar, que só existe nas telas de gestão. */
    readonly manageable = input(false);
    readonly reverse = output<Transaction>();

    protected readonly display = TRANSACTION_DISPLAY;
}
