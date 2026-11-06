import { Component, computed, input } from '@angular/core';

const WIDTH = 100;
const HEIGHT = 32;
const PADDING = 2;

/** Tendência sem eixos. Em SVG, e não em canvas, porque a tabela pode ter muitas linhas. */
@Component({
    selector: 'app-sparkline',
    host: { class: 'inline-block align-middle' },
    template: `@if (path(); as points) {
        <svg role="img" class="block w-24 h-8" [class]="colorClass()" [attr.aria-label]="label()" [attr.viewBox]="'0 0 ' + width + ' ' + height" preserveAspectRatio="none">
            <polyline fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke" [attr.points]="points" />
        </svg>
    }`
})
export class Sparkline {
    readonly values = input.required<number[]>();
    readonly label = input.required<string>();

    protected readonly width = WIDTH;
    protected readonly height = HEIGHT;

    protected readonly path = computed(() => {
        const values = this.values();
        if (values.length < 2) {
            return null;
        }
        const min = Math.min(...values);
        const span = Math.max(...values) - min;
        const usable = HEIGHT - PADDING * 2;
        return values
            .map((value, index) => {
                const x = (index / (values.length - 1)) * WIDTH;
                const y = span === 0 ? HEIGHT / 2 : PADDING + usable - ((value - min) / span) * usable;
                return `${x.toFixed(2)},${y.toFixed(2)}`;
            })
            .join(' ');
    });

    protected readonly colorClass = computed(() => {
        const values = this.values();
        const change = values[values.length - 1] - values[0];
        return change > 0 ? 'text-green-700 dark:text-green-400' : change < 0 ? 'text-red-700 dark:text-red-400' : 'text-muted-color';
    });
}
