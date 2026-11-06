import { CurrencyPipe } from '@angular/common';
import { Component, input } from '@angular/core';

@Component({
    selector: 'app-stat-card',
    imports: [CurrencyPipe],
    template: `<div class="card mb-0! h-full">
        <div class="flex justify-between gap-4 mb-4">
            <div>
                <div class="text-muted-color font-medium mb-4">{{ label() }}</div>
                <div class="text-surface-900 dark:text-surface-0 font-medium text-2xl">{{ value() | currency: 'BRL' : 'symbol' : '1.2-2' }}</div>
            </div>
            <div class="flex items-center justify-center shrink-0 bg-primary-100 dark:bg-primary-400/10 rounded-border w-10 h-10">
                <i class="text-primary text-xl! {{ icon() }}" aria-hidden="true"></i>
            </div>
        </div>
        <span class="text-muted-color">{{ hint() }}</span>
    </div>`
})
export class StatCard {
    readonly label = input.required<string>();
    readonly value = input.required<number>();
    readonly icon = input.required<string>();
    readonly hint = input.required<string>();
}
