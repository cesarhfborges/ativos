import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { ChartModule } from 'primeng/chart';
import { chartTheme } from '@/app/core/chart-theme';
import { RadarAxis } from '@/app/core/portfolio-radar';
import { LayoutService } from '@/app/layout/service/layout.service';

/** Um polígono precisa de pelo menos três pontos. */
const MIN_AXES = 3;
const FILL_ALPHA = '33';

/** O formato da carteira em seis notas. A tabela traz o valor real por trás de cada uma e explica como é medida. */
@Component({
    selector: 'app-portfolio-radar-chart',
    imports: [ChartModule, DecimalPipe],
    template: `<div class="card mb-0! h-full">
        <h2 class="text-xl! font-semibold mt-0! mb-1!">Perfil da carteira</h2>
        <p class="text-muted-color mt-0! mb-6!">Notas de 0 a 100 para comparar aspectos da carteira. Descrevem como ela está hoje e não são recomendação de investimento.</p>

        <div class="flex flex-col lg:flex-row lg:items-center gap-8">
            @if (plotted().length >= minAxes) {
                <div class="h-96 w-full lg:w-[28rem] shrink-0">
                    <p-chart type="radar" height="100%" [data]="chartData()" [options]="chartOptions()" [ariaLabel]="description()" />
                </div>
            } @else {
                <p class="text-muted-color m-0! lg:w-[28rem] shrink-0">Ainda não há dados suficientes para desenhar o perfil. Ele aparece quando a carteira tiver posições com cotação.</p>
            }

            <div class="grow min-w-0 overflow-x-auto" tabindex="0" role="region" aria-label="Notas do perfil da carteira">
                <table class="w-full">
                    <caption class="sr-only">Notas do perfil da carteira e o que cada uma mede</caption>
                    <thead>
                        <tr>
                            <th scope="col" class="py-2 text-left">Aspecto</th>
                            <th scope="col" class="py-2 pl-4 text-right">Nota</th>
                            <th scope="col" class="py-2 pl-4 text-left">Hoje</th>
                        </tr>
                    </thead>
                    <tbody>
                        @for (axis of axes(); track axis.key) {
                            <tr>
                                <th scope="row" class="py-2 text-left font-medium align-top">
                                    {{ axis.label }}
                                    <div class="text-muted-color text-sm font-normal">{{ axis.hint }}</div>
                                </th>
                                <td class="py-2 pl-4 text-right tabular-nums font-medium align-top">{{ axis.score === null ? '—' : (axis.score | number: '1.0-0') }}</td>
                                <td class="py-2 pl-4 align-top">{{ axis.detail }}</td>
                            </tr>
                        }
                    </tbody>
                </table>
            </div>
        </div>
    </div>`
})
export class PortfolioRadarChart {
    private readonly layoutService = inject(LayoutService);

    readonly axes = input.required<RadarAxis[]>();

    protected readonly minAxes = MIN_AXES;
    private readonly theme = computed(() => chartTheme(this.layoutService.isDarkTheme()));

    /** Um aspecto sem dados sai do desenho, em vez de parecer uma nota zero. */
    protected readonly plotted = computed(() => this.axes().filter((axis): axis is RadarAxis & { score: number } => axis.score !== null));

    protected readonly description = computed(() => `Gráfico de radar do perfil da carteira, com notas de 0 a 100: ${this.plotted().map((axis) => `${axis.label} ${Math.round(axis.score)}`).join(', ')}.`);

    protected readonly chartData = computed(() => {
        const theme = this.theme();
        const color = theme.series[0];
        return {
            labels: this.plotted().map((axis) => axis.label),
            datasets: [
                {
                    label: 'Nota',
                    data: this.plotted().map((axis) => axis.score),
                    borderColor: color,
                    backgroundColor: `${color}${FILL_ALPHA}`,
                    borderWidth: 2,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    pointBackgroundColor: color,
                    pointBorderColor: theme.surface,
                    pointBorderWidth: 2,
                    pointHitRadius: 16
                }
            ]
        };
    });

    protected readonly chartOptions = computed(() => {
        const theme = this.theme();
        return {
            maintainAspectRatio: false,
            plugins: {
                // Uma só série: o título do cartão já diz o que ela é.
                legend: { display: false },
                tooltip: { callbacks: { label: (item: { parsed: { r: number } }) => `${Math.round(item.parsed.r)} de 100` } }
            },
            scales: {
                r: {
                    min: 0,
                    max: 100,
                    // A grade marca cada 25 pontos; só o meio e o topo têm número, para não disputar espaço com os dados.
                    ticks: { stepSize: 25, color: theme.text, backdropColor: 'transparent', callback: (value: number) => (value === 50 || value === 100 ? value : '') },
                    grid: { color: theme.grid },
                    angleLines: { color: theme.grid },
                    pointLabels: { color: theme.text, font: { size: 13 } }
                }
            }
        };
    });
}
