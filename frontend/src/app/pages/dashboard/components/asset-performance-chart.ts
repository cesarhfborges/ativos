import { DecimalPipe, formatDate } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { ChartModule } from 'primeng/chart';
import { ChartResize } from '@/app/shared/chart-resize';
import { DashboardPosition, MarketAsset } from '@/app/core/api.models';
import { chartTheme, MAX_LINE_SERIES } from '@/app/core/chart-theme';
import { alignIndexed, shortHistoryDays } from '@/app/core/market';
import { LayoutService } from '@/app/layout/service/layout.service';
import { PercentChange } from '@/app/shared/percent-change';

const BASE = 100;
/** Até aqui o backend devolve um ponto por hora; acima, um por dia. */
const MAX_DAYS_HOURLY = 7;

const decimal = (value: number) => value.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** Preços muito diferentes só são comparáveis na mesma escala: todos partem de 100 no início do período. */
@Component({
    selector: 'app-asset-performance-chart',
    imports: [ChartModule, ChartResize, DecimalPipe, PercentChange],
    template: `<div class="card mb-0! h-full">
        <h2 class="text-xl! font-semibold mt-0! mb-1!">Desempenho dos ativos</h2>
        <p class="text-muted-color mt-0! mb-6!">{{ subtitle() }}</p>

        @if (lines().length === 0) {
            <p class="text-muted-color m-0!">Ainda não há preços suficientes neste período. O histórico é buscado na próxima coleta de cotações.</p>
        } @else {
            <div class="h-80">
                <p-chart appChartResize type="line" height="100%" [data]="chartData()" [options]="chartOptions()" [ariaLabel]="ariaDescription()" />
            </div>
            <details class="mt-4">
                <summary class="cursor-pointer text-muted-color">Ver os dados em tabela</summary>
                <table class="w-full mt-2">
                    <thead>
                        <tr>
                            <th scope="col" class="py-1 text-left">Ativo</th>
                            <th scope="col" class="py-1 text-right">Índice atual</th>
                            <th scope="col" class="py-1 text-right">Variação</th>
                        </tr>
                    </thead>
                    <tbody>
                        @for (line of lines(); track line.symbol) {
                            <tr>
                                <th scope="row" class="py-1 text-left font-medium">{{ line.symbol }}</th>
                                <td class="py-1 text-right tabular-nums">{{ line.last | number: '1.1-1' }}</td>
                                <td class="py-1 text-right"><app-percent-change [value]="line.last - base" /></td>
                            </tr>
                        }
                    </tbody>
                </table>
            </details>
        }
    </div>`
})
export class AssetPerformanceChart {
    private readonly layoutService = inject(LayoutService);

    readonly market = input.required<MarketAsset[]>();
    readonly positions = input.required<DashboardPosition[]>();
    readonly days = input.required<number>();
    readonly periodLabel = input.required<string>();

    protected readonly base = BASE;

    private readonly theme = computed(() => chartTheme(this.layoutService.isDarkTheme()));

    /** As maiores posições a valor de mercado, com as cores por ordem alfabética: mudar o ranking não troca a cor de ninguém. */
    private readonly selected = computed(() => {
        const valueOf = new Map(this.positions().map((position) => [position.symbol, position.marketValue]));
        return this.market()
            .filter((asset) => asset.prices.length >= 2)
            .sort((a, b) => (valueOf.get(b.symbol) ?? 0) - (valueOf.get(a.symbol) ?? 0))
            .slice(0, MAX_LINE_SERIES)
            .sort((a, b) => a.symbol.localeCompare(b.symbol));
    });

    private readonly hourly = computed(() => this.days() <= MAX_DAYS_HOURLY);

    private readonly aligned = computed(() => {
        const bucket = this.hourly() ? 'yyyy-MM-dd HH' : 'yyyy-MM-dd';
        return alignIndexed(this.selected(), (time) => formatDate(time, bucket, 'pt-BR'));
    });

    protected readonly lines = computed(() =>
        this.aligned().series.map((series) => ({
            symbol: series.symbol,
            last: series.values.filter((value) => value !== null).at(-1) ?? BASE
        }))
    );

    protected readonly subtitle = computed(() => {
        const hidden = this.market().filter((asset) => asset.prices.length >= 2).length - this.selected().length;
        const base = `Preço de cada ativo, com todos valendo 100 no início do período de ${this.periodLabel()}.`;
        const short = this.selected().flatMap((asset) => {
            const covered = shortHistoryDays(asset.prices, this.days());
            return covered === null ? [] : [`${asset.symbol} (${covered} ${covered === 1 ? 'dia' : 'dias'})`];
        });
        const history = short.length > 0 ? ` Com menos histórico, parte de 100 no primeiro preço: ${short.join(', ')}.` : '';
        return `${base}${history}${hidden > 0 ? ` Mostra as ${MAX_LINE_SERIES} maiores posições.` : ''}`;
    });

    protected readonly ariaDescription = computed(() => {
        const parts = this.lines().map((line) => `${line.symbol} ${decimal(line.last)}`);
        return `Gráfico de linhas do preço dos ativos com base 100 no início do período. Valores atuais: ${parts.join(', ')}.`;
    });

    protected readonly chartData = computed(() => {
        const { times, series } = this.aligned();
        const theme = this.theme();
        const format = this.hourly() ? 'dd/MM HH:mm' : 'dd/MM/yy';
        return {
            labels: times.map((time) => formatDate(time, format, 'pt-BR')),
            datasets: series.map((line, index) => ({
                label: line.symbol,
                data: line.values,
                borderColor: theme.series[index],
                backgroundColor: theme.series[index],
                borderWidth: 2,
                tension: 0.2,
                spanGaps: true,
                pointRadius: 0,
                pointHoverRadius: 4,
                pointHoverBorderWidth: 2,
                pointHoverBorderColor: theme.surface
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
                tooltip: {
                    callbacks: {
                        label: (item: { dataset: { label: string }; parsed: { y: number } }) => {
                            const change = item.parsed.y - BASE;
                            return `${item.dataset.label}: ${change >= 0 ? '+' : '−'}${decimal(Math.abs(change))}%`;
                        }
                    }
                }
            },
            scales: {
                x: { ticks: { color: theme.text, maxTicksLimit: 7, maxRotation: 0 }, grid: { display: false } },
                y: {
                    ticks: { color: theme.text },
                    // A linha do 100 marca onde cada ativo começou o período.
                    grid: { color: (context: { tick?: { value: number } }) => (context.tick?.value === BASE ? theme.baseline : theme.grid) }
                }
            }
        };
    });
}
