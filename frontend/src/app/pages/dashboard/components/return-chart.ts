import { DecimalPipe, formatDate } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { ChartModule } from 'primeng/chart';
import { PortfolioHistoryPoint } from '@/app/core/api.models';
import { chartTheme } from '@/app/core/chart-theme';
import { axisDateFormat, returnSeries } from '@/app/core/market';
import { LayoutService } from '@/app/layout/service/layout.service';

const TABLE_POINTS = 8;

const percent = (value: number) => `${value > 0 ? '+' : value < 0 ? '−' : ''}${Math.abs(value).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;

/** Separa o que é valorização do que é aporte: comprar mais aumenta o patrimônio, não o retorno. */
@Component({
    selector: 'app-return-chart',
    imports: [ChartModule, DecimalPipe],
    template: `<div class="card mb-0! h-full">
        <h2 class="text-xl! font-semibold mt-0! mb-1!">Retorno da carteira</h2>
        <p class="text-muted-color mt-0! mb-6!">Lucro ou prejuízo sobre o investido, sem o efeito das compras.</p>

        @if (points().length < 2) {
            <p class="text-muted-color m-0!">São necessários ao menos dois registros no período. Um novo é feito a cada hora cheia.</p>
        } @else {
            <div class="h-72">
                <p-chart type="line" height="100%" [data]="chartData()" [options]="chartOptions()" [ariaLabel]="ariaDescription()" />
            </div>
            <details class="mt-4">
                <summary class="cursor-pointer text-muted-color">Ver os dados mais recentes em tabela</summary>
                <table class="w-full mt-2">
                    <thead>
                        <tr>
                            <th scope="col" class="py-1 text-left">Data</th>
                            <th scope="col" class="py-1 text-right">Retorno</th>
                        </tr>
                    </thead>
                    <tbody>
                        @for (point of recentPoints(); track point.time) {
                            <tr>
                                <td class="py-1">{{ point.label }}</td>
                                <td class="py-1 text-right tabular-nums">{{ point.percent | number: '1.2-2' }}%</td>
                            </tr>
                        }
                    </tbody>
                </table>
            </details>
        }
    </div>`
})
export class ReturnChart {
    private readonly layoutService = inject(LayoutService);

    readonly history = input.required<PortfolioHistoryPoint[]>();

    protected readonly points = computed(() => returnSeries(this.history()).map((point) => ({ ...point, label: formatDate(point.time, 'short', 'pt-BR') })));
    protected readonly recentPoints = computed(() => this.points().slice(-TABLE_POINTS).reverse());

    protected readonly ariaDescription = computed(() => {
        const points = this.points();
        return `Gráfico de linha do retorno da carteira: de ${percent(points[0]?.percent ?? 0)} a ${percent(points.at(-1)?.percent ?? 0)} no período.`;
    });

    protected readonly chartData = computed(() => {
        const color = chartTheme(this.layoutService.isDarkTheme()).series[0];
        const points = this.points();
        const format = axisDateFormat(points.map((point) => point.time));
        return {
            labels: points.map((point) => formatDate(point.time, format, 'pt-BR')),
            datasets: [
                {
                    label: 'Retorno',
                    data: points.map((point) => point.percent),
                    borderColor: color,
                    backgroundColor: color,
                    borderWidth: 2,
                    tension: 0.2,
                    pointRadius: points.length > 20 ? 0 : 3,
                    pointHoverRadius: 5
                }
            ]
        };
    });

    protected readonly chartOptions = computed(() => {
        const theme = chartTheme(this.layoutService.isDarkTheme());
        return {
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                // Com uma só série, o título do card já diz o que a linha representa.
                legend: { display: false },
                tooltip: { callbacks: { label: (item: { parsed: { y: number } }) => `Retorno: ${percent(item.parsed.y)}` } }
            },
            scales: {
                x: { ticks: { color: theme.text, maxTicksLimit: 5, maxRotation: 0 }, grid: { display: false } },
                y: {
                    ticks: { color: theme.text, callback: (value: number | string) => percent(Number(value)) },
                    // A linha do zero separa ganho de perda.
                    grid: { color: (context: { tick?: { value: number } }) => (context.tick?.value === 0 ? theme.baseline : theme.grid) }
                }
            }
        };
    });
}
