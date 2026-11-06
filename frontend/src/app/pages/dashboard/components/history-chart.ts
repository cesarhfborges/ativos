import { CurrencyPipe, DatePipe, formatCurrency, formatDate } from '@angular/common';
import { Component, computed, inject, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChartModule } from 'primeng/chart';
import { SelectButtonModule } from 'primeng/selectbutton';
import { PortfolioHistoryPoint } from '@/app/core/api.models';
import { LayoutService } from '@/app/layout/service/layout.service';

const TABLE_POINTS = 12;

const THEME = {
    light: { line: '#047857', fill: 'rgba(4, 120, 87, 0.10)', text: '#64748b', grid: 'rgba(100, 116, 139, 0.15)' },
    dark: { line: '#34d399', fill: 'rgba(52, 211, 153, 0.12)', text: '#a1a1aa', grid: 'rgba(161, 161, 170, 0.15)' }
};

const brl = (value: number) => formatCurrency(value, 'pt-BR', 'R$', 'BRL', '1.2-2');

@Component({
    selector: 'app-history-chart',
    imports: [ChartModule, CurrencyPipe, DatePipe, FormsModule, SelectButtonModule],
    template: `<div class="card mb-0!">
        <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
            <h2 id="history-title" class="text-xl! font-semibold m-0!">Evolução patrimonial</h2>
            <p-selectbutton ariaLabelledBy="history-title" optionLabel="label" optionValue="value" [options]="periods" [unselectable]="true" [(ngModel)]="days" />
        </div>

        @if (points().length === 0) {
            <p class="text-muted-color m-0!">Ainda não há histórico neste período. Um novo ponto é registrado a cada hora cheia, quando as cotações são coletadas.</p>
        } @else {
            <div class="h-72">
                <p-chart type="line" height="100%" [data]="chartData()" [options]="chartOptions()" [ariaLabel]="description()" />
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
    readonly days = model(30);

    protected readonly periods = [
        { label: '7 dias', value: 7 },
        { label: '30 dias', value: 30 },
        { label: '90 dias', value: 90 },
        { label: '1 ano', value: 365 }
    ];

    private readonly theme = computed(() => THEME[this.layoutService.isDarkTheme() ? 'dark' : 'light']);

    protected readonly recentPoints = computed(() => this.points().slice(-TABLE_POINTS).reverse());

    protected readonly description = computed(() => {
        const points = this.points();
        const first = points[0];
        const last = points[points.length - 1];
        return first && last ? `Gráfico de linha do patrimônio: de ${brl(first.totalBalance)} a ${brl(last.totalBalance)} no período.` : '';
    });

    protected readonly chartData = computed(() => {
        const theme = this.theme();
        return {
            labels: this.points().map((point) => formatDate(point.time, 'short', 'pt-BR')),
            datasets: [
                {
                    label: 'Patrimônio',
                    data: this.points().map((point) => point.totalBalance),
                    borderColor: theme.line,
                    backgroundColor: theme.fill,
                    borderWidth: 2,
                    fill: true,
                    tension: 0.25,
                    pointRadius: this.points().length > 30 ? 0 : 3,
                    pointHoverRadius: 5,
                    pointBackgroundColor: theme.line
                }
            ]
        };
    });

    protected readonly chartOptions = computed(() => {
        const theme = this.theme();
        return {
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                // Com uma só série, o título do card já diz o que a linha representa.
                legend: { display: false },
                tooltip: { callbacks: { label: (item: { parsed: { y: number } }) => `Patrimônio: ${brl(item.parsed.y)}` } }
            },
            scales: {
                x: { ticks: { color: theme.text, maxTicksLimit: 6, maxRotation: 0 }, grid: { display: false } },
                y: { ticks: { color: theme.text, callback: (value: number | string) => brl(Number(value)) }, grid: { color: theme.grid } }
            }
        };
    });
}
