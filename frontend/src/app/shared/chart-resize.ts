import { afterNextRender, DestroyRef, Directive, ElementRef, inject } from '@angular/core';
import { UIChart } from 'primeng/chart';

/**
 * Ajusta o gráfico ao espaço disponível sempre que a janela ou o contêiner mudam, como ao abrir o menu lateral.
 * Vale em conjunto com o `contain: inline-size` do `p-chart` em styles.scss: sem ele, o canvas estica o contêiner,
 * e o gráfico mediria a própria largura antiga.
 */
@Directive({
    selector: 'p-chart[appChartResize]',
    host: { '(window:resize)': 'schedule()' }
})
export class ChartResize {
    private readonly chart = inject(UIChart);
    private frame: number | null = null;

    constructor() {
        const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
        const destroyRef = inject(DestroyRef);
        afterNextRender(() => {
            const observer = new ResizeObserver(() => this.schedule());
            observer.observe(host);
            destroyRef.onDestroy(() => observer.disconnect());
        });
        destroyRef.onDestroy(() => {
            if (this.frame !== null) {
                cancelAnimationFrame(this.frame);
            }
        });
    }

    /** Um redimensionamento dispara vários eventos: o gráfico só é redesenhado uma vez por quadro. */
    protected schedule(): void {
        this.frame ??= requestAnimationFrame(() => {
            this.frame = null;
            this.chart.chart?.resize();
        });
    }
}
