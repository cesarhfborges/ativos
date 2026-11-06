import { Component, computed, inject, input } from '@angular/core';
import { chartTheme } from '@/app/core/chart-theme';
import { GAUGE_CENTER, gaugeArc, gaugePoint, SENTIMENT_BANDS, sentimentZone } from '@/app/core/sentiment';
import { LayoutService } from '@/app/layout/service/layout.service';

const ARC_RADIUS = 96;
const NEEDLE_RADIUS = 82;
const TICK_INNER = 105;
const TICK_OUTER = 109;
/** Por fora do arco: por dentro, o ponteiro passaria por cima dos números. */
const LABEL_RADIUS = 118;
/** Um respiro em pontos de nota entre as cores, para as faixas não se fundirem. */
const GAP = 0.6;
const SCALE_LABELS = [0, 25, 50, 75, 100];

/** O ponteiro indica a nota, e o número e o nome da faixa ficam sempre por escrito: a cor nunca fala sozinha. */
@Component({
    selector: 'app-sentiment-gauge',
    template: `<figure class="m-0">
        <svg viewBox="-14 -6 268 140" class="block w-full max-w-80 mx-auto" role="img" [attr.aria-label]="description()">
            @for (arc of arcs(); track arc.key) {
                <path [attr.d]="arc.path" [attr.stroke]="arc.color" fill="none" stroke-width="18" />
            }
            @for (tick of ticks; track tick.score) {
                <line [attr.x1]="tick.inner.x" [attr.y1]="tick.inner.y" [attr.x2]="tick.outer.x" [attr.y2]="tick.outer.y" [attr.stroke]="theme().baseline" stroke-width="1.5" />
                <text [attr.x]="tick.label.x" [attr.y]="tick.label.y" [attr.fill]="theme().text" font-size="10" text-anchor="middle" dominant-baseline="middle">{{ tick.score }}</text>
            }
            <line class="needle" [attr.x1]="center.x" [attr.y1]="center.y" [attr.x2]="needle().x" [attr.y2]="needle().y" stroke="currentColor" stroke-width="3" stroke-linecap="round" />
            <circle [attr.cx]="center.x" [attr.cy]="center.y" r="7" fill="currentColor" [attr.stroke]="theme().surface" stroke-width="2" />
        </svg>
        <figcaption class="text-center mt-2">
            <div class="text-5xl font-semibold tabular-nums leading-none">{{ rounded() }}</div>
            <div class="text-xl font-medium mt-2">{{ zone() }}</div>
        </figcaption>
    </figure>`,
    styles: `
        .needle {
            transition:
                x2 0.4s ease,
                y2 0.4s ease;
        }

        @media (prefers-reduced-motion: reduce) {
            .needle {
                transition: none;
            }
        }
    `
})
export class SentimentGauge {
    private readonly layoutService = inject(LayoutService);

    readonly score = input.required<number>();

    protected readonly center = GAUGE_CENTER;
    protected readonly theme = computed(() => chartTheme(this.layoutService.isDarkTheme()));
    protected readonly ticks = SCALE_LABELS.map((score) => ({
        score,
        inner: gaugePoint(score, TICK_INNER),
        outer: gaugePoint(score, TICK_OUTER),
        label: gaugePoint(score, LABEL_RADIUS)
    }));

    protected readonly rounded = computed(() => Math.round(this.score()));
    protected readonly zone = computed(() => sentimentZone(this.score()).label);
    protected readonly needle = computed(() => gaugePoint(this.score(), NEEDLE_RADIUS));
    protected readonly description = computed(() => `Medidor de ganância e medo, de 0 a 100: nota ${this.rounded()}, ${this.zone()}.`);

    protected readonly arcs = computed(() => {
        const colors = this.theme().sentiment;
        // As pontas da escala não têm vizinho, então não precisam do respiro.
        return SENTIMENT_BANDS.map(({ tone, from, to }) => ({
            key: tone,
            color: colors[tone],
            path: gaugeArc(from === 0 ? 0 : from + GAP, to === 100 ? 100 : to - GAP, ARC_RADIUS)
        }));
    });
}
