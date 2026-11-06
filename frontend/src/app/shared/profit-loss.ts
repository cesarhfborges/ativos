import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';

/** Ganho em verde e perda em vermelho, sempre com sinal e ícone: a cor nunca é a única indicação. */
@Component({
    selector: 'app-profit-loss',
    imports: [CurrencyPipe, DecimalPipe],
    host: { class: 'inline-block' },
    template: `<span class="font-medium whitespace-nowrap" [class]="colorClass()">
        <i class="text-sm! mr-1 {{ icon() }}" aria-hidden="true"></i>{{ sign() }} {{ absolute() | currency: 'BRL' : 'symbol' : '1.2-2' }}
        @if (percent() !== null) {
            <span class="font-normal" [class.block]="stacked()">({{ sign() }}{{ absolutePercent() | number: '1.2-2' }}%)</span>
        }
    </span>`
})
export class ProfitLoss {
    readonly value = input.required<number>();
    readonly percent = input<number | null>(null);
    /** Percentual em uma segunda linha, para colunas estreitas. */
    readonly stacked = input(false);

    protected readonly absolute = computed(() => Math.abs(this.value()));
    protected readonly absolutePercent = computed(() => Math.abs(this.percent() ?? 0));
    protected readonly sign = computed(() => (this.value() > 0 ? '+' : this.value() < 0 ? '−' : ''));
    protected readonly icon = computed(() => (this.value() > 0 ? 'pi pi-arrow-up-right' : this.value() < 0 ? 'pi pi-arrow-down-right' : 'pi pi-minus'));
    protected readonly colorClass = computed(() => (this.value() > 0 ? 'text-green-700 dark:text-green-400' : this.value() < 0 ? 'text-red-700 dark:text-red-400' : 'text-muted-color'));
}
