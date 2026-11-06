import { CurrencyPipe, formatCurrency, formatDate } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { ChartModule } from 'primeng/chart';
import { WatchlistItem } from '@/app/core/api.models';
import { chartTheme } from '@/app/core/chart-theme';
import { PlanKind, planTargets } from '@/app/core/watchlist';
import { LayoutService } from '@/app/layout/service/layout.service';

/** Até aqui o backend devolve um ponto por hora; acima, um por dia. */
const MAX_DAYS_HOURLY = 7;

/** A cor segue o tipo de alvo, para não mudar de um ativo para outro. A primeira da paleta é a do preço. */
const TARGET_COLOR_INDEX: Record<PlanKind, number> = { SELL: 1, BUY: 2, STOP: 4 };
/** Tracejados diferentes: os alvos continuam distinguíveis sem depender da cor. */
const TARGET_DASH: Record<PlanKind, number[]> = { SELL: [8, 4], BUY: [2, 3], STOP: [12, 3, 2, 3] };

const money = (value: number) => formatCurrency(value, 'pt-BR', 'R$', 'BRL', '1.2-8');

/** O preço no período e as linhas dos alvos, para ver a distância até eles ao longo do tempo. */
@Component({
    selector: 'app-watchlist-chart',
    imports: [ChartModule, CurrencyPipe],
    template: `@if (item().prices.length < 2) {
            <p class="text-surface-600 dark:text-surface-300 m-0!">Ainda não há preços suficientes neste período. O histórico é buscado logo depois de adicionar o ativo.</p>
        } @else {
            <div class="h-72">
                <p-chart type="line" height="100%" [data]="chartData()" [options]="chartOptions()" [ariaLabel]="description()" />
            </div>
            <dl class="grid grid-cols-2 gap-4 mt-4 mb-0">
                <div>
                    <dt class="text-surface-600 dark:text-surface-300 text-sm">Mínimo em {{ periodLabel() }}</dt>
                    <dd class="m-0 font-medium tabular-nums">{{ item().periodLow | currency: 'BRL' : 'symbol' : '1.2-8' }}</dd>
                </div>
                <div>
                    <dt class="text-surface-600 dark:text-surface-300 text-sm">Máximo em {{ periodLabel() }}</dt>
                    <dd class="m-0 font-medium tabular-nums">{{ item().periodHigh | currency: 'BRL' : 'symbol' : '1.2-8' }}</dd>
                </div>
            </dl>
        }`
})
export class WatchlistChart {
    private readonly layoutService = inject(LayoutService);

    readonly item = input.required<WatchlistItem>();
    readonly days = input.required<number>();
    readonly periodLabel = input.required<string>();

    private readonly theme = computed(() => chartTheme(this.layoutService.isDarkTheme()));
    private readonly targets = computed(() => planTargets(this.item()));

    protected readonly description = computed(() => {
        const { symbol, periodLow, periodHigh } = this.item();
        const targets = this.targets().map((target) => `${target.label} ${money(target.price)}`);
        const range = periodLow !== null && periodHigh !== null ? `, entre ${money(periodLow)} e ${money(periodHigh)}` : '';
        return `Gráfico de linha do preço de ${symbol} em ${this.periodLabel()}${range}.${targets.length ? ` Alvos: ${targets.join(', ')}.` : ''}`;
    });

    protected readonly chartData = computed(() => {
        const theme = this.theme();
        const prices = this.item().prices;
        const format = this.days() <= MAX_DAYS_HOURLY ? 'dd/MM HH:mm' : 'dd/MM/yy';
        return {
            labels: prices.map((point) => formatDate(point.time, format, 'pt-BR')),
            datasets: [
                {
                    label: 'Preço',
                    data: prices.map((point) => point.price),
                    borderColor: theme.series[0],
                    backgroundColor: theme.series[0],
                    borderWidth: 2,
                    tension: 0.2,
                    pointRadius: 0,
                    pointHoverRadius: 4,
                    pointHoverBorderWidth: 2,
                    pointHoverBorderColor: theme.surface
                },
                ...this.targets().map((target) => ({
                    label: target.label,
                    data: prices.map(() => target.price),
                    borderColor: theme.series[TARGET_COLOR_INDEX[target.kind]],
                    backgroundColor: theme.series[TARGET_COLOR_INDEX[target.kind]],
                    borderWidth: 2,
                    borderDash: TARGET_DASH[target.kind],
                    pointRadius: 0,
                    pointHoverRadius: 0
                }))
            ]
        };
    });

    protected readonly chartOptions = computed(() => {
        const theme = this.theme();
        return {
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { position: 'bottom', labels: { color: theme.text, usePointStyle: true, pointStyle: 'line', boxWidth: 24 } },
                tooltip: { callbacks: { label: (context: { dataset: { label: string }; parsed: { y: number } }) => `${context.dataset.label}: ${money(context.parsed.y)}` } }
            },
            scales: {
                x: { ticks: { color: theme.text, maxTicksLimit: 7, maxRotation: 0 }, grid: { display: false } },
                y: { ticks: { color: theme.text }, grid: { color: theme.grid } }
            }
        };
    });
}
