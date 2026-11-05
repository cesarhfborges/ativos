import { CurrencyPipe, DecimalPipe, formatCurrency } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { ChartModule } from 'primeng/chart';
import { AllocationKey, AllocationSlice } from '@/app/core/portfolio';
import { LayoutService } from '@/app/layout/service/layout.service';

/** Cor fixa por categoria, escolhida por tema e validada contra daltonismo e contraste com a superfície. */
const COLORS: Record<'light' | 'dark', Record<AllocationKey, string>> = {
    light: { CRYPTO: '#2a78d6', STOCK: '#eb6834' },
    dark: { CRYPTO: '#3987e5', STOCK: '#d95926' }
};

@Component({
    selector: 'app-allocation-chart',
    imports: [ChartModule, CurrencyPipe, DecimalPipe],
    template: `<div class="card mb-0 h-full">
        <h2 class="text-xl! font-semibold mt-0! mb-1!">Alocação da carteira</h2>
        <p class="text-muted-color mt-0! mb-6!">Criptomoedas e ações, a valor de mercado.</p>

        <div class="flex flex-col md:flex-row md:items-center gap-8">
            @if (hasData()) {
                <div class="w-56 max-w-full mx-auto md:mx-0 shrink-0">
                    <p-chart type="doughnut" [data]="chartData()" [options]="chartOptions" [ariaLabel]="description()" />
                </div>
            } @else {
                <p class="text-muted-color m-0! md:w-56 shrink-0">Sua carteira ainda está vazia. Registre uma compra para ver a distribuição.</p>
            }

            <table class="w-full max-w-lg">
                <caption class="sr-only">
                    Distribuição do patrimônio por categoria
                </caption>
                <thead class="sr-only">
                    <tr>
                        <th scope="col">Categoria</th>
                        <th scope="col">Valor</th>
                        <th scope="col">Percentual</th>
                    </tr>
                </thead>
                <tbody>
                    @for (slice of slices(); track slice.key) {
                        <tr>
                            <th scope="row" class="py-2 font-normal text-left"><span class="inline-block w-3 h-3 rounded-sm mr-2" [style.background-color]="palette()[slice.key]"></span>{{ slice.label }}</th>
                            <td class="py-2 text-right">{{ slice.value | currency: 'BRL' : 'symbol' : '1.2-2' }}</td>
                            <td class="py-2 pl-4 text-right font-medium w-20">{{ slice.percent | number: '1.1-1' }}%</td>
                        </tr>
                    }
                </tbody>
            </table>
        </div>
    </div>`
})
export class AllocationChart {
    private readonly layoutService = inject(LayoutService);

    readonly slices = input.required<AllocationSlice[]>();

    protected readonly palette = computed(() => COLORS[this.layoutService.isDarkTheme() ? 'dark' : 'light']);
    private readonly visibleSlices = computed(() => this.slices().filter((slice) => slice.value > 0));

    protected readonly hasData = computed(() => this.visibleSlices().length > 0);

    protected readonly description = computed(() => {
        const parts = this.slices().map((slice) => `${slice.label} ${slice.percent.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`);
        return `Gráfico de rosca da alocação da carteira: ${parts.join(', ')}.`;
    });

    protected readonly chartData = computed(() => {
        const palette = this.palette();
        const slices = this.visibleSlices();
        return {
            labels: slices.map((slice) => slice.label),
            datasets: [
                {
                    data: slices.map((slice) => slice.value),
                    backgroundColor: slices.map((slice) => palette[slice.key]),
                    hoverBackgroundColor: slices.map((slice) => palette[slice.key]),
                    borderWidth: 0,
                    // Com só uma categoria não há o que separar, e o espaçamento abriria um corte no anel.
                    spacing: slices.length > 1 ? 2 : 0
                }
            ]
        };
    });

    protected readonly chartOptions = {
        cutout: '65%',
        plugins: {
            // A tabela ao lado do gráfico já faz o papel de legenda, com valores e percentuais.
            legend: { display: false },
            tooltip: {
                callbacks: {
                    label: (item: { parsed: number }) => formatCurrency(item.parsed, 'pt-BR', 'R$', 'BRL', '1.2-2')
                }
            }
        }
    };
}
