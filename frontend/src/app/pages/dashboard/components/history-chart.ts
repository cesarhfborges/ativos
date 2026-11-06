import { CurrencyPipe, DatePipe, formatCurrency, formatDate } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { ChartModule } from 'primeng/chart';
import { ChartResize } from '@/app/shared/chart-resize';
import { PortfolioHistoryPoint } from '@/app/core/api.models';
import { chartTheme } from '@/app/core/chart-theme';
import { axisDateFormat } from '@/app/core/market';
import { LayoutService } from '@/app/layout/service/layout.service';

const TABLE_POINTS = 12;

const brl = (value: number) => formatCurrency(value, 'pt-BR', 'R$', 'BRL', '1.2-2');

@Component({
    selector: 'app-history-chart',
    imports: [ChartModule, ChartResize, CurrencyPipe, DatePipe],
    template: `<div class="card mb-0! h-full">
        <h2 class="text-xl! font-semibold mt-0! mb-1!">Evolução patrimonial</h2>
        <p class="text-muted-color mt-0! mb-6!">Quanto a carteira vale e quanto custou. O espaço entre as linhas é o lucro ou prejuízo.</p>

        @if (points().length === 0) {
            <p class="text-muted-color m-0!">Ainda não há histórico neste período. Um novo ponto é registrado a cada hora cheia, quando as cotações são coletadas.</p>
        } @else {
            <div class="h-72">
                <p-chart appChartResize type="line" height="100%" [data]="chartData()" [options]="chartOptions()" [ariaLabel]="description()" />
            </div>
            <details class="mt-4">
                <summary class="cursor-pointer text-muted-color">Ver os dados mais recentes em tabela</summary>
                <table class="w-full mt-2">
                    <thead>
                        <tr>
                            <th scope="col" class="py-1 text-left">Data</th>
                            <th scope="col" class="py-1 text-right">Investido</th>
                            <th scope="col" class="py-1 text-right">Patrimônio</th>
                        </tr>
                    </thead>
                    <tbody>
                        @for (point of recentPoints(); track point.time) {
                            <tr>
                                <td class="py-1">{{ point.time | date: 'short' }}</td>
                                <td class="py-1 text-right">{{ point.investedBalance | currency: 'BRL' : 'symbol' : '1.2-2' }}</td>
                                <td class="py-1 text-right font-medium">{{ point.totalBalance | currency: 'BRL' : 'symbol' : '1.2-2' }}</td>
                            </tr>
                        }
                    </tbody>
                </table>
            </details>
        }
    </div>`
})
export class HistoryChart {
    private readonly layoutService = inject(LayoutService);

    readonly points = input.required<PortfolioHistoryPoint[]>();

    private readonly theme = computed(() => chartTheme(this.layoutService.isDarkTheme()));

    protected readonly recentPoints = computed(() => this.points().slice(-TABLE_POINTS).reverse());

    protected readonly description = computed(() => {
        const points = this.points();
        const first = points[0];
        const last = points[points.length - 1];
        return first && last ? `Gráfico de linhas: patrimônio de ${brl(first.totalBalance)} a ${brl(last.totalBalance)} no período; valor investido de ${brl(first.investedBalance)} a ${brl(last.investedBalance)}.` : '';
    });

    protected readonly chartData = computed(() => {
        const theme = this.theme();
        const format = axisDateFormat(this.points().map((point) => point.time));
        return {
            labels: this.points().map((point) => formatDate(point.time, format, 'pt-BR')),
            datasets: [
                { label: 'Patrimônio', data: this.points().map((point) => point.totalBalance), color: theme.series[0] },
                { label: 'Investido', data: this.points().map((point) => point.investedBalance), color: theme.series[1] }
            ].map(({ color, ...series }) => ({
                ...series,
                borderColor: color,
                backgroundColor: color,
                borderWidth: 2,
                tension: 0.2,
                pointRadius: this.points().length > 20 ? 0 : 3,
                pointHoverRadius: 5
            }))
        };
    });

    protected readonly chartOptions = computed(() => {
        const theme = this.theme();
        return {
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { position: 'bottom', labels: { color: theme.text, usePointStyle: true, pointStyle: 'line', boxWidth: 24 } },
                tooltip: { callbacks: { label: (item: { dataset: { label: string }; parsed: { y: number } }) => `${item.dataset.label}: ${brl(item.parsed.y)}` } }
            },
            scales: {
                x: { ticks: { color: theme.text, maxTicksLimit: 7, maxRotation: 0 }, grid: { display: false } },
                y: { ticks: { color: theme.text, callback: (value: number | string) => brl(Number(value)) }, grid: { color: theme.grid } }
            }
        };
    });
}
