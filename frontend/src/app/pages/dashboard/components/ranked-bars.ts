import { Component, computed, input } from '@angular/core';
import { RankedItem } from '@/app/core/market';

/**
 * Barras horizontais em ordem, em HTML: cada valor fica escrito ao lado da barra e a tabela já é a versão acessível.
 * Com valores negativos, as barras nascem de uma linha de zero; ganho e perda se distinguem também pelo lado e pelo sinal.
 */
@Component({
    selector: 'app-ranked-bars',
    template: `<div class="card mb-0! h-full">
        <h2 class="text-xl! font-semibold mt-0! mb-1!">{{ heading() }}</h2>
        <p class="text-muted-color mt-0! mb-6!">{{ description() }}</p>

        @if (rows().length === 0) {
            <p class="text-muted-color m-0!">{{ emptyMessage() }}</p>
        } @else {
            <table class="w-full border-collapse">
                <caption class="sr-only">
                    {{
                        heading()
                    }}
                </caption>
                <thead class="sr-only">
                    <tr>
                        <th scope="col">Item</th>
                        <th scope="col">Proporção</th>
                        <th scope="col">Valor</th>
                    </tr>
                </thead>
                <tbody>
                    @for (row of rows(); track row.label) {
                        <tr>
                            <th scope="row" class="py-2 pr-3 w-16 text-left font-medium whitespace-nowrap">{{ row.label }}</th>
                            <td class="py-2" aria-hidden="true">
                                <div class="relative h-2">
                                    @if (hasNegative()) {
                                        <div class="absolute -top-1 -bottom-1 w-px bg-surface-400 dark:bg-surface-500" [style.left.%]="zero()"></div>
                                    }
                                    <div class="absolute inset-y-0 rounded-sm min-w-0.5" [class]="row.barClass" [style.left.%]="row.left" [style.width.%]="row.width"></div>
                                </div>
                            </td>
                            <td class="py-2 pl-3 w-px text-right whitespace-nowrap tabular-nums">{{ row.text }}</td>
                        </tr>
                    }
                </tbody>
            </table>
        }
    </div>`
})
export class RankedBars {
    readonly heading = input.required<string>();
    readonly description = input.required<string>();
    readonly emptyMessage = input.required<string>();
    readonly items = input.required<RankedItem[]>();
    readonly format = input.required<(value: number) => string>();
    /** Colore pelo sinal, quando a barra é ganho ou perda; sem isso, a cor é neutra, para não sugerir ganho. */
    readonly signed = input(false);

    private readonly maxPositive = computed(() => Math.max(0, ...this.items().map((item) => item.value)));
    private readonly maxNegative = computed(() => Math.max(0, ...this.items().map((item) => -item.value)));
    private readonly span = computed(() => this.maxPositive() + this.maxNegative() || 1);

    protected readonly hasNegative = computed(() => this.maxNegative() > 0);
    protected readonly zero = computed(() => (this.maxNegative() / this.span()) * 100);

    protected readonly rows = computed(() => {
        const span = this.span();
        const zero = this.zero();
        const format = this.format();
        return this.items().map((item) => {
            const width = (Math.abs(item.value) / span) * 100;
            return {
                label: item.label,
                text: format(item.value),
                left: item.value < 0 ? zero - width : zero,
                width,
                barClass: !this.signed() ? 'bg-blue-600 dark:bg-blue-400' : item.value < 0 ? 'bg-red-700 dark:bg-red-400' : 'bg-green-700 dark:bg-green-400'
            };
        });
    });
}
