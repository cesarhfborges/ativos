import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { DashboardPosition, MarketAsset } from '@/app/core/api.models';
import { composition, movers24h, portfolioChange24h } from '@/app/core/market';
import { PercentChange } from '@/app/shared/percent-change';
import { ProfitLoss } from '@/app/shared/profit-loss';

/** O que mudou nas últimas 24 horas e onde a carteira está mais exposta. */
@Component({
    selector: 'app-market-highlights',
    imports: [DecimalPipe, PercentChange, ProfitLoss],
    host: { class: 'grid grid-cols-12 gap-4 sm:gap-8' },
    template: `
        <div class="col-span-12 md:col-span-6 2xl:col-span-3">
            <div class="card mb-0! h-full">
                <div class="text-muted-color font-medium mb-4">Carteira em 24 horas</div>
                @if (change(); as change) {
                    <div class="text-xl"><app-profit-loss [value]="change.value" [percent]="change.percent" /></div>
                } @else {
                    <div class="text-xl text-muted-color">—</div>
                }
                <div class="text-muted-color mt-4">Com as quantidades de hoje</div>
            </div>
        </div>
        <div class="col-span-12 md:col-span-6 2xl:col-span-3">
            <div class="card mb-0! h-full">
                <div class="text-muted-color font-medium mb-4">Maior alta em 24 horas</div>
                @if (movers().up; as mover) {
                    <div class="text-xl flex flex-wrap items-baseline gap-x-3">
                        <span class="font-medium">{{ mover.symbol }}</span>
                        <app-percent-change [value]="mover.percent" />
                    </div>
                } @else {
                    <div class="text-xl text-muted-color">Nenhum ativo subiu</div>
                }
                <div class="text-muted-color mt-4">Entre os ativos da carteira</div>
            </div>
        </div>
        <div class="col-span-12 md:col-span-6 2xl:col-span-3">
            <div class="card mb-0! h-full">
                <div class="text-muted-color font-medium mb-4">Maior queda em 24 horas</div>
                @if (movers().down; as mover) {
                    <div class="text-xl flex flex-wrap items-baseline gap-x-3">
                        <span class="font-medium">{{ mover.symbol }}</span>
                        <app-percent-change [value]="mover.percent" />
                    </div>
                } @else {
                    <div class="text-xl text-muted-color">Nenhum ativo caiu</div>
                }
                <div class="text-muted-color mt-4">Entre os ativos da carteira</div>
            </div>
        </div>
        <div class="col-span-12 md:col-span-6 2xl:col-span-3">
            <div class="card mb-0! h-full">
                <div class="text-muted-color font-medium mb-4">Maior posição</div>
                @if (largest(); as item) {
                    <div class="text-xl flex flex-wrap items-baseline gap-x-3">
                        <span class="font-medium">{{ item.label }}</span>
                        <span>{{ item.value | number: '1.1-1' }}%</span>
                    </div>
                } @else {
                    <div class="text-xl text-muted-color">—</div>
                }
                <div class="text-muted-color mt-4">Peso no patrimônio</div>
            </div>
        </div>
    `
})
export class MarketHighlights {
    readonly positions = input.required<DashboardPosition[]>();
    readonly market = input.required<MarketAsset[]>();

    protected readonly change = computed(() => portfolioChange24h(this.positions(), this.market()));
    protected readonly movers = computed(() => movers24h(this.market()));
    protected readonly largest = computed(() => composition(this.positions())[0] ?? null);
}
