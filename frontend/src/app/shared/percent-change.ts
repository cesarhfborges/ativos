import { DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';

/** Variação percentual com sinal e ícone, para a cor nunca ser a única indicação. Sem valor, mostra um traço. */
@Component({
    selector: 'app-percent-change',
    imports: [DecimalPipe],
    host: { class: 'inline-block' },
    template: `@if (value() === null) {
            <span class="text-muted-color" aria-hidden="true">—</span><span class="sr-only">Sem dados</span>
        } @else {
            <span class="font-medium whitespace-nowrap" [class]="colorClass()"><i class="text-xs! mr-1 {{ icon() }}" aria-hidden="true"></i>{{ sign() }}{{ absolute() | number: '1.2-2' }}%</span>
        }`
})
export class PercentChange {
    readonly value = input.required<number | null>();

    private readonly direction = computed(() => Math.sign(this.value() ?? 0));

    protected readonly absolute = computed(() => Math.abs(this.value() ?? 0));
    protected readonly sign = computed(() => (this.direction() > 0 ? '+' : this.direction() < 0 ? '−' : ''));
    protected readonly icon = computed(() => (this.direction() > 0 ? 'pi pi-arrow-up-right' : this.direction() < 0 ? 'pi pi-arrow-down-right' : 'pi pi-minus'));
    protected readonly colorClass = computed(() => (this.direction() > 0 ? 'text-green-700 dark:text-green-400' : this.direction() < 0 ? 'text-red-700 dark:text-red-400' : 'text-muted-color'));
}
