import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { AssetType, DashboardPosition, Position } from '@/app/core/api.models';
import { ProfitLoss } from './profit-loss';

const ASSET_TYPE_LABELS: Record<AssetType, string> = { STOCK: 'Ação', CRYPTO: 'Cripto' };

@Component({
    selector: 'app-positions-table',
    imports: [ButtonModule, CurrencyPipe, DatePipe, DecimalPipe, ProfitLoss, TableModule],
    template: `@if (rows().length === 0) {
            <p class="text-center text-muted-color py-8 m-0!">Você ainda não tem posições abertas. Registre sua primeira compra.</p>
        } @else {
            <p-table [value]="rows()" dataKey="symbol" sortField="marketValue" [sortOrder]="-1" [tableStyle]="{ 'min-width': '64rem' }">
                <ng-template #header>
                    <tr>
                        <th scope="col" pSortableColumn="symbol">Ativo <p-sortIcon field="symbol" /></th>
                        <th scope="col" pSortableColumn="quantity" class="text-right!">Quantidade <p-sortIcon field="quantity" /></th>
                        <th scope="col" pSortableColumn="averagePrice" class="text-right!">Preço médio <p-sortIcon field="averagePrice" /></th>
                        <th scope="col" pSortableColumn="currentPrice" class="text-right!">Cotação <p-sortIcon field="currentPrice" /></th>
                        <th scope="col" pSortableColumn="marketValue" class="text-right!">Valor a mercado <p-sortIcon field="marketValue" /></th>
                        <th scope="col" pSortableColumn="profitLoss" class="text-right!">Lucro/Prejuízo <p-sortIcon field="profitLoss" /></th>
                        <th scope="col" pSortableColumn="share" class="text-right!">% da carteira <p-sortIcon field="share" /></th>
                        <th scope="col" class="text-right!">Ações</th>
                    </tr>
                </ng-template>
                <ng-template #body let-row>
                    <tr>
                        <td>
                            <div class="font-medium">{{ row.symbol }}</div>
                            <div class="text-muted-color text-sm">{{ row.subtitle }}</div>
                        </td>
                        <td class="text-right">{{ row.quantity | number: '1.0-8' }}</td>
                        <td class="text-right">{{ row.averagePrice | currency: 'BRL' : 'symbol' : '1.2-4' }}</td>
                        <td class="text-right">
                            @if (row.currentPrice !== null) {
                                <div>{{ row.currentPrice | currency: 'BRL' : 'symbol' : '1.2-4' }}</div>
                                <div class="text-muted-color text-sm">{{ row.quoteUpdatedAt | date: 'short' }}</div>
                            } @else {
                                <span class="text-muted-color"><i class="pi pi-exclamation-circle mr-1" aria-hidden="true"></i>Sem cotação</span>
                            }
                        </td>
                        <td class="text-right">{{ row.marketValue | currency: 'BRL' : 'symbol' : '1.2-2' }}</td>
                        <td class="text-right">
                            @if (row.currentPrice !== null) {
                                <app-profit-loss [value]="row.profitLoss" [percent]="row.profitLossPercent" />
                            } @else {
                                <span class="text-muted-color">—</span>
                            }
                        </td>
                        <td class="text-right">{{ row.share | number: '1.1-1' }}%</td>
                        <td class="text-right whitespace-nowrap">
                            <p-button label="Comprar" size="small" [text]="true" [ariaLabel]="'Comprar mais ' + row.symbol" (onClick)="buy.emit(row)" />
                            <p-button label="Vender" size="small" severity="secondary" [text]="true" [ariaLabel]="'Vender ' + row.symbol" (onClick)="sell.emit(row)" />
                        </td>
                    </tr>
                </ng-template>
            </p-table>
        }`
})
export class PositionsTable {
    readonly positions = input.required<DashboardPosition[]>();
    /** Patrimônio usado como base do percentual de cada posição. */
    readonly total = input.required<number>();
    readonly buy = output<Position>();
    readonly sell = output<Position>();

    protected readonly rows = computed(() =>
        this.positions().map((position) => ({
            ...position,
            subtitle: position.name === position.symbol ? ASSET_TYPE_LABELS[position.assetType] : `${position.name} · ${ASSET_TYPE_LABELS[position.assetType]}`,
            share: this.total() > 0 ? (position.marketValue / this.total()) * 100 : 0
        }))
    );
}
