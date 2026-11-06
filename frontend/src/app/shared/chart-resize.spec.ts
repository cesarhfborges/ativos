import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UIChart } from 'primeng/chart';
import { ChartResize } from './chart-resize';

const nextFrames = async () => {
    await new Promise(requestAnimationFrame);
    await new Promise(requestAnimationFrame);
};

@Component({
    selector: 'app-host',
    imports: [UIChart, ChartResize],
    template: `<div [style.width.px]="width()"><p-chart appChartResize type="line" height="100%" [data]="data" /></div>`
})
class Host {
    readonly width = signal(400);
    readonly data = { labels: ['a'], datasets: [{ data: [1] }] };
}

describe('ChartResize', () => {
    const setup = async () => {
        const fixture = TestBed.createComponent(Host);
        fixture.detectChanges();
        await fixture.whenStable();
        await nextFrames();
        const chart = fixture.debugElement.children[0].children[0].componentInstance as UIChart;
        const resize = jasmine.createSpy('resize');
        chart.chart = { resize, destroy: () => undefined };
        return { fixture, resize };
    };

    it('redesenha quando a janela muda', async () => {
        const { resize } = await setup();
        window.dispatchEvent(new Event('resize'));
        await nextFrames();
        expect(resize).toHaveBeenCalled();
    });

    it('junta vários eventos em um só redesenho', async () => {
        const { resize } = await setup();
        await nextFrames();
        resize.calls.reset();
        for (let i = 0; i < 5; i++) {
            window.dispatchEvent(new Event('resize'));
        }
        await nextFrames();
        expect(resize).toHaveBeenCalledTimes(1);
    });

    it('redesenha quando só o contêiner muda', async () => {
        const { fixture, resize } = await setup();
        fixture.componentInstance.width.set(250);
        fixture.detectChanges();
        await nextFrames();
        await nextFrames();
        expect(resize).toHaveBeenCalled();
    });

    it('para de observar quando sai da tela', async () => {
        const { fixture, resize } = await setup();
        await nextFrames();
        fixture.destroy();
        resize.calls.reset();
        window.dispatchEvent(new Event('resize'));
        await nextFrames();
        expect(resize).not.toHaveBeenCalled();
    });
});
