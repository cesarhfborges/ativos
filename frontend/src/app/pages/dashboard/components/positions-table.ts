import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { AssetType, Position } from '@/app/core/api.models';
import { positionCost } from '@/app/core/portfolio';

const ASSET_TYPE_LABELS: Record<AssetType, string> = { STOCK: 'Ação', CRYPTO: 'Cripto' };

@Component({
    selector: 'app-positions-table',
    imports: [ButtonModule, CurrencyPipe, DecimalPipe, TableModule],
    template: `@if (positions().length === 0) {
            <p class="text-center text-muted-color py-8 m-0!">Você ainda não tem posições abertas. Registre sua primeira compra.</p>
        } @else {
            <p-table [value]="positions()" dataKey="symbol" [tableStyle]="{ 'min-width': '44rem' }">
                <ng-template #header>
                    <tr>
                        <th scope="col">Ativo</th>
                        <th scope="col">Tipo</th>
                        <th scope="col" class="text-right!">Quantidade</th>
                        <th scope="col" class="text-right!">Preço médio</th>
                        <th scope="col" class="text-right!">Valor investido</th>
                        <th scope="col" class="text-right!">Ações</th>
                    </tr>
                </ng-template>
                <ng-template #body let-position>
                    <tr>
                        <td>
                            <div class="font-medium">{{ position.symbol }}</div>
                            @if (position.name !== position.symbol) {
                                <div class="text-muted-color text-sm">{{ position.name }}</div>
                            }
                        </td>
                        <td>{{ typeLabel(position.assetType) }}</td>
                        <td class="text-right">{{ position.quantity | number: '1.0-8' }}</td>
                        <td class="text-right">{{ position.averagePrice | currency: 'BRL' : 'symbol' : '1.2-4' }}</td>
                        <td class="text-right">{{ cost(position) | currency: 'BRL' : 'symbol' : '1.2-4' }}</td>
                        <td class="text-right whitespace-nowrap">
                            <p-button label="Comprar" size="small" [text]="true" [ariaLabel]="'Comprar mais ' + position.symbol" (onClick)="buy.emit(position)" />
                            <p-button label="Vender" size="small" severity="secondary" [text]="true" [ariaLabel]="'Vender ' + position.symbol" (onClick)="sell.emit(position)" />
                        </td>
                    </tr>
                </ng-template>
            </p-table>
        }`
})
export class PositionsTable {
    readonly positions = input.required<Position[]>();
    readonly buy = output<Position>();
    readonly sell = output<Position>();

    protected readonly cost = positionCost;

    protected typeLabel(type: AssetType): string {
        return ASSET_TYPE_LABELS[type];
    }
}
