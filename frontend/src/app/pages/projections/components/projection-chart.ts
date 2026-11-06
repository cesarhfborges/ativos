import { formatCurrency, formatDate } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { ChartModule } from 'primeng/chart';
import { ChartResize } from '@/app/shared/chart-resize';
import { Scenario, SimulationResponse } from '@/app/core/api.models';
import { LayoutService } from '@/app/layout/service/layout.service';

const THEME = {
    light: { real: '#047857', selected: '#2a78d6', band: 'rgba(42, 120, 214, 0.15)', other: '#94a3b8', text: '#475569', grid: 'rgba(100, 116, 139, 0.15)' },
    dark: { real: '#199e70', selected: '#3987e5', band: 'rgba(57, 135, 229, 0.2)', other: '#71717a', text: '#d4d4d8', grid: 'rgba(161, 161, 170, 0.15)' }
};

export const SCENARIO_LABELS: Record<Scenario, string> = {
    PESSIMISTIC: 'Pessimista',
    REALISTIC: 'Realista',
    OPTIMISTIC: 'Otimista'
};

const BAND_LOWER_LABEL = 'Limite inferior';
const brl = (value: number) => formatCurrency(value, 'pt-BR', 'R$', 'BRL', '1.2-2');
const compactBrl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', notation: 'compact', maximumFractionDigits: 1 });
const toX = (date: string) => new Date(`${date}T12:00:00Z`).getTime();

/** A linha do cenário escolhido fica em destaque, com sua faixa provável; as outras servem de referência. */
@Component({
    selector: 'app-projection-chart',
    imports: [ChartModule, ChartResize],
    template: `<div class="h-80 lg:h-96">
        <p-chart appChartResize type="line" height="100%" [data]="data()" [options]="options()" [ariaLabel]="description()" />
    </div>`
})
export class ProjectionChart {
    private readonly layoutService = inject(LayoutService);

    readonly scenarios = input.required<Record<Scenario, SimulationResponse>>();
    readonly selected = input.required<Scenario>();

    private readonly colors = computed(() => THEME[this.layoutService.isDarkTheme() ? 'dark' : 'light']);
    private readonly active = computed(() => this.scenarios()[this.selected()]);

    protected readonly description = computed(() => {
        const last = this.active().projection.at(-1);
        return last ? `Gráfico de linha do patrimônio real e projetado no cenário ${SCENARIO_LABELS[this.selected()].toLowerCase()}: ${brl(last.expected)} ao fim do período, entre ${brl(last.lower)} e ${brl(last.upper)}.` : '';
    });

    /** O último ponto real é o valor de hoje, que liga a curva real ao início da projeção. */
    private readonly realPoints = computed(() => {
        const { history, projection, currentValue } = this.active();
        const today = projection[0]?.date;
        return [...history.filter((point) => point.date !== today).map((point) => ({ x: toX(point.date), y: point.value })), { x: toX(today), y: currentValue }];
    });

    protected readonly data = computed(() => {
        const colors = this.colors();
        const active = this.active();
        const others = (Object.keys(SCENARIO_LABELS) as Scenario[]).filter((scenario) => scenario !== this.selected());
        const line = (values: SimulationResponse['projection'], field: 'expected' | 'lower' | 'upper') => values.map((point) => ({ x: toX(point.date), y: point[field] }));

        return {
            datasets: [
                { label: 'Patrimônio real', data: this.realPoints(), borderColor: colors.real, backgroundColor: colors.real, borderWidth: 2, pointRadius: this.realPoints().length > 30 ? 0 : 3 },
                { label: SCENARIO_LABELS[this.selected()], data: line(active.projection, 'expected'), borderColor: colors.selected, backgroundColor: colors.selected, borderWidth: 3, borderDash: [2, 4], pointRadius: 0, pointHoverRadius: 5 },
                ...others.map((scenario) => ({
                    label: SCENARIO_LABELS[scenario],
                    data: line(this.scenarios()[scenario].projection, 'expected'),
                    borderColor: colors.other,
                    backgroundColor: colors.other,
                    borderWidth: 1.5,
                    borderDash: scenario === 'PESSIMISTIC' ? [2, 3] : [6, 4],
                    pointRadius: 0,
                    pointHoverRadius: 4
                })),
                { label: 'Faixa provável', data: line(active.projection, 'upper'), borderColor: colors.band, borderWidth: 4, backgroundColor: colors.band, pointRadius: 0, pointHoverRadius: 0, fill: '+1' },
                { label: BAND_LOWER_LABEL, data: line(active.projection, 'lower'), borderColor: colors.band, borderWidth: 1, backgroundColor: colors.band, pointRadius: 0, pointHoverRadius: 0 }
            ]
        };
    });

    protected readonly options = computed(() => {
        const colors = this.colors();
        const xs = [...this.realPoints().map((point) => point.x), ...this.active().projection.map((point) => toX(point.date))];
        return {
            maintainAspectRatio: false,
            parsing: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: { color: colors.text, usePointStyle: true, pointStyle: 'line', filter: (item: { text: string }) => item.text !== BAND_LOWER_LABEL }
                },
                tooltip: {
                    filter: (item: { dataset: { label: string } }) => item.dataset.label !== BAND_LOWER_LABEL && item.dataset.label !== 'Faixa provável',
                    callbacks: {
                        title: (items: { parsed: { x: number } }[]) => (items[0] ? formatDate(items[0].parsed.x, 'MM/yyyy', 'pt-BR', 'UTC') : ''),
                        label: (item: { dataset: { label: string }; parsed: { y: number } }) => `${item.dataset.label}: ${brl(item.parsed.y)}`
                    }
                }
            },
            scales: {
                x: {
                    type: 'linear',
                    min: Math.min(...xs),
                    max: Math.max(...xs),
                    ticks: { color: colors.text, maxTicksLimit: 7, maxRotation: 0, callback: (value: number | string) => formatDate(Number(value), 'MMM/yy', 'pt-BR', 'UTC') },
                    grid: { display: false }
                },
                y: { ticks: { color: colors.text, maxTicksLimit: 6, callback: (value: number | string) => compactBrl.format(Number(value)) }, grid: { color: colors.grid } }
            }
        };
    });
}
