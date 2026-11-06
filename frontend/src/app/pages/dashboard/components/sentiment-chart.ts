import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { MessageModule } from 'primeng/message';
import { ChartModule } from 'primeng/chart';
import { PortfolioSentiment } from '@/app/core/api.models';
import { chartTheme } from '@/app/core/chart-theme';
import { coverageNote, SENTIMENT_BANDS, SENTIMENT_TICKS, SENTIMENT_ZONES, sentimentComponents, sentimentDay, sentimentZone } from '@/app/core/sentiment';
import { LayoutService } from '@/app/layout/service/layout.service';
import { ChartResize } from '@/app/shared/chart-resize';
import { SentimentGauge } from './sentiment-gauge';

const TABLE_POINTS = 12;
/** Com mais pontos que isso, os marcadores viram ruído sobre a linha. */
const MAX_POINTS_WITH_MARKERS = 20;

const whole = (value: number) => Math.round(value).toLocaleString('pt-BR');

/** O que o Chart.js entrega às cores calculadas. A escala vai de 0 a 100, então a nota é a posição na altura do gráfico. */
interface LineContext {
    chart: { ctx: CanvasRenderingContext2D; chartArea?: { top: number; bottom: number } };
}

interface PointContext {
    parsed?: { y: number };
}

/** A linha muda de cor exatamente onde cruza a faixa: as paradas repetidas fazem o corte, sem tons intermediários. */
@Component({
    selector: 'app-sentiment-chart',
    imports: [ChartModule, ChartResize, DecimalPipe, MessageModule, SentimentGauge],
    template: `<div class="card mb-0! h-full">
        <h2 class="text-xl! font-semibold mt-0! mb-1!">Ganância e medo</h2>
        <p class="text-muted-color mt-0! mb-6!">De 0 (medo extremo) a 100 (ganância extrema), a partir dos preços dos seus ativos, com o peso que cada um tem hoje. Uma compra ou venda redesenha a curva. Descreve o comportamento dos preços, sem volume nem redes sociais, e não é recomendação de investimento.</p>

        @if (sentiment(); as data) {
            @if (data.stale) {
                <p-message severity="warn" icon="pi pi-exclamation-triangle" text="As notas não são atualizadas há mais de 5 dias." styleClass="mb-6" />
            }
            @if (data.current; as current) {
                <div class="flex flex-col lg:flex-row gap-8">
                    <div class="lg:w-80 shrink-0 min-w-0 lg:self-center">
                        <app-sentiment-gauge [score]="current.score" />
                        <p class="text-muted-color text-center mt-2! mb-6!">Leitura de {{ day(current.day) }}</p>
                        @if (coverage(); as note) {
                            <p class="text-muted-color text-sm mt-0! mb-4!">{{ note }}</p>
                        }
                    </div>

                    <div class="grow min-w-0">
                        <div class="h-96">
                            <p-chart appChartResize type="line" height="100%" [data]="chartData()" [options]="chartOptions()" [ariaLabel]="description()" />
                        </div>
                        <ul class="list-none flex flex-wrap gap-x-4 gap-y-1 p-0 m-0 mt-4 text-sm text-muted-color">
                            @for (item of zones; track item.label; let index = $index) {
                                <li class="flex items-center gap-2">
                                    <span class="inline-block w-3 h-3 rounded-sm shrink-0" aria-hidden="true" [style.background-color]="theme().sentiment[item.tone]"></span>
                                    <span><span class="tabular-nums">{{ item.from }}–{{ (zones[index + 1]?.from ?? 101) - 1 }}</span> {{ item.label }}</span>
                                </li>
                            }
                        </ul>
                        <details class="mt-4">
                            <summary class="cursor-pointer text-muted-color">Ver os dados mais recentes em tabela</summary>
                            <table class="w-full mt-2">
                                <caption class="sr-only">Índice de ganância e medo por dia, do mais recente ao mais antigo</caption>
                                <thead>
                                    <tr>
                                        <th scope="col" class="py-1 text-left">Data</th>
                                        <th scope="col" class="py-1 text-right">Nota</th>
                                        <th scope="col" class="py-1 pl-4 text-left">Faixa</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    @for (row of recentRows(); track row.day) {
                                        <tr>
                                            <td class="py-1">{{ row.day }}</td>
                                            <td class="py-1 text-right tabular-nums font-medium">{{ row.score | number: '1.0-0' }}</td>
                                            <td class="py-1 pl-4">{{ row.zone }}</td>
                                        </tr>
                                    }
                                </tbody>
                            </table>
                        </details>
                    </div>
                </div>

                <h3 class="text-base! font-medium mt-8! mb-4!">O que compõe a nota</h3>
                <dl class="grid grid-cols-1 sm:grid-cols-2 2xl:grid-cols-4 gap-x-8 gap-y-6 m-0">
                    @for (component of components(); track component.key) {
                        <div class="min-w-0">
                            <dt class="font-medium">
                                {{ component.label }}
                                <span class="sr-only">: nota</span>
                            </dt>
                            <dd class="m-0">
                                <div class="text-2xl font-semibold tabular-nums">{{ component.score | number: '1.0-0' }}</div>
                                <div class="text-muted-color text-sm mt-1">{{ component.hint }}</div>
                            </dd>
                        </div>
                    }
                </dl>
            } @else {
                <p class="text-muted-color m-0!">Ainda não há dados suficientes. O índice precisa de 51 fechamentos diários dos ativos que somam pelo menos metade da carteira, e é atualizado a cada hora cheia, quando as cotações são coletadas.</p>
            }
        }
    </div>`
})
export class SentimentChart {
    private readonly layoutService = inject(LayoutService);

    readonly sentiment = input.required<PortfolioSentiment | null>();

    protected readonly zones = SENTIMENT_ZONES;
    protected readonly day = sentimentDay;

    protected readonly theme = computed(() => chartTheme(this.layoutService.isDarkTheme()));
    private readonly points = computed(() => this.sentiment()?.points ?? []);

    private readonly current = computed(() => this.sentiment()?.current ?? null);
    protected readonly components = computed(() => {
        const current = this.current();
        return current ? sentimentComponents(current) : [];
    });
    protected readonly coverage = computed(() => {
        const current = this.current();
        return current ? coverageNote(current.coverage) : null;
    });

    protected readonly recentRows = computed(() =>
        this.points()
            .slice(-TABLE_POINTS)
            .reverse()
            .map((point) => ({ day: sentimentDay(point.day), score: point.score, zone: sentimentZone(point.score).label }))
    );

    protected readonly description = computed(() => {
        const points = this.points();
        const first = points[0];
        const last = points[points.length - 1];
        return first && last ? `Gráfico de linhas do índice de ganância e medo: de ${whole(first.score)} em ${sentimentDay(first.day)} a ${whole(last.score)} em ${sentimentDay(last.day)}, ${sentimentZone(last.score).label}.` : '';
    });

    protected readonly chartData = computed(() => {
        const colors = this.theme().sentiment;
        const points = this.points();
        const pointColor = (context: PointContext) => colors[sentimentZone(context.parsed?.y ?? 50).tone];
        return {
            labels: points.map((point) => sentimentDay(point.day)),
            datasets: [
                {
                    label: 'Nota',
                    data: points.map((point) => point.score),
                    borderColor: ({ chart: { ctx, chartArea } }: LineContext) => {
                        // Na primeira passada o Chart.js ainda não mediu a área do gráfico.
                        if (!chartArea) {
                            return colors.neutral;
                        }
                        const gradient = ctx.createLinearGradient(0, chartArea.bottom, 0, chartArea.top);
                        SENTIMENT_BANDS.forEach(({ tone, from, to }) => {
                            gradient.addColorStop(from / 100, colors[tone]);
                            gradient.addColorStop(to / 100, colors[tone]);
                        });
                        return gradient;
                    },
                    backgroundColor: pointColor,
                    pointBackgroundColor: pointColor,
                    pointBorderColor: pointColor,
                    pointHoverBackgroundColor: pointColor,
                    pointHoverBorderColor: pointColor,
                    borderWidth: 3,
                    tension: 0.2,
                    pointRadius: points.length > MAX_POINTS_WITH_MARKERS ? 0 : 3,
                    pointHoverRadius: 5
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
                // Uma só série: o título do cartão já diz o que ela é.
                legend: { display: false },
                tooltip: { callbacks: { label: (item: { parsed: { y: number } }) => `${whole(item.parsed.y)}: ${sentimentZone(item.parsed.y).label}` } }
            },
            scales: {
                x: { ticks: { color: theme.text, maxTicksLimit: 7, maxRotation: 0 }, grid: { display: false } },
                y: {
                    min: 0,
                    max: 100,
                    // A grade marca as mudanças de faixa, em vez de intervalos iguais.
                    afterBuildTicks: (axis: { ticks: { value: number }[] }) => {
                        axis.ticks = SENTIMENT_TICKS.map((value) => ({ value }));
                    },
                    ticks: { color: theme.text },
                    grid: { color: theme.grid }
                }
            }
        };
    });
}
