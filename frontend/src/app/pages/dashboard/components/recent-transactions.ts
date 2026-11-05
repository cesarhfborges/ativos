import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Transaction } from '@/app/core/api.models';
import { TRANSACTION_DISPLAY } from '@/app/core/transaction-display';

@Component({
    selector: 'app-recent-transactions',
    imports: [CurrencyPipe, DatePipe, RouterLink],
    template: `<div class="card mb-0 h-full">
        <div class="flex items-center justify-between gap-4 mb-4">
            <h2 class="text-xl! font-semibold m-0!">Últimos lançamentos</h2>
            <a routerLink="/extrato" class="text-primary font-medium underline">Ver extrato</a>
        </div>

        @if (transactions().length === 0) {
            <p class="text-muted-color m-0!">Nenhum lançamento registrado ainda.</p>
        } @else {
            <ul class="list-none m-0 p-0">
                @for (transaction of transactions(); track transaction.id) {
                    <li class="flex items-center justify-between gap-4 py-3 border-b border-surface-200 dark:border-surface-700 last:border-b-0">
                        <div class="flex items-center gap-3 min-w-0">
                            <i class="text-muted-color {{ display[transaction.type].icon }}" aria-hidden="true"></i>
                            <div class="min-w-0">
                                <div class="font-medium truncate">
                                    {{ display[transaction.type].label }}
                                    {{ transaction.symbol }}
                                </div>
                                <div class="text-muted-color text-sm">{{ transaction.timestamp | date: 'short' }}</div>
                            </div>
                        </div>
                        <div class="font-medium whitespace-nowrap">{{ transaction.totalAmount | currency: 'BRL' : 'symbol' : '1.2-2' }}</div>
                    </li>
                }
            </ul>
        }
    </div>`
})
export class RecentTransactions {
    readonly transactions = input.required<Transaction[]>();

    protected readonly display = TRANSACTION_DISPLAY;
}
