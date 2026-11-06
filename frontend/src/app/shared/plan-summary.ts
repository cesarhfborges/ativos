import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { WatchlistItem } from '@/app/core/api.models';
import { planTargets } from '@/app/core/watchlist';

/** Os preços do plano e quanto falta para cada um. O alvo atingido tem ícone e texto, para a cor não ser a única indicação. */
@Component({
    selector: 'app-plan-summary',
    imports: [CurrencyPipe, DecimalPipe],
    template: `@if (targets().length === 0) {
            <span class="text-surface-600 dark:text-surface-300">Sem plano</span>
        } @else {
            <ul class="list-none m-0 p-0 flex flex-col gap-1">
                @for (target of targets(); track target.kind) {
                    <li [class.font-semibold]="target.reached" [class.text-surface-600]="!target.active" [class.dark:text-surface-300]="!target.active">
                        @if (target.reached) {
                            <i class="pi pi-flag-fill mr-1 text-xs!" aria-hidden="true"></i>
                        }
                        {{ target.label }} {{ target.price | currency: 'BRL' : 'symbol' : '1.2-8' }}:
                        @if (target.reached) {
                            no alvo
                        } @else if (!target.active) {
                            sem posição
                        } @else if (target.distancePercent !== null) {
                            faltam {{ target.distancePercent | number: '1.1-1' }}%
                        } @else {
                            sem cotação
                        }
                    </li>
                }
            </ul>
        }`
})
export class PlanSummary {
    readonly item = input.required<WatchlistItem>();

    protected readonly targets = computed(() => planTargets(this.item()));
}
