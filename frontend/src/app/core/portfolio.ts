import { DashboardPosition, Position } from './api.models';

export type AllocationKey = Position['assetType'];

export interface AllocationSlice {
    key: AllocationKey;
    label: string;
    value: number;
    /** Percentual de 0 a 100 sobre o patrimônio. */
    percent: number;
}

const ALLOCATION_LABELS: Record<AllocationKey, string> = {
    CRYPTO: 'Criptomoedas',
    STOCK: 'Ações'
};

/** Mesma escala e arredondamento (HALF_UP) dos valores monetários no backend. */
export function roundMoney(value: number): number {
    const sign = Math.sign(value);
    return (sign * Math.round((Math.abs(value) + Number.EPSILON) * 1e4)) / 1e4;
}

/** Valor total de uma compra, com a taxa. */
export function buyTotal(quantity: number, unitPrice: number, fee: number): number {
    return roundMoney(quantity * unitPrice + fee);
}

/** Valor líquido de uma venda; negativo quando a taxa supera o valor vendido. */
export function sellNet(quantity: number, unitPrice: number, fee: number): number {
    return roundMoney(quantity * unitPrice - fee);
}

/** Preço médio ponderado depois da compra; a taxa não entra no cálculo, como no backend. */
export function averageAfterBuy(position: Position, quantity: number, unitPrice: number): number {
    return roundMoney((position.quantity * position.averagePrice + quantity * unitPrice) / (position.quantity + quantity));
}

/** Resultado da venda sobre o preço médio, já descontada a taxa. */
export function saleResult(position: Position, quantity: number, unitPrice: number, fee: number): number {
    return roundMoney(sellNet(quantity, unitPrice, fee) - quantity * position.averagePrice);
}

/** Distribuição do patrimônio a mercado, com as duas categorias sempre presentes e na mesma ordem. */
export function allocation(positions: DashboardPosition[]): AllocationSlice[] {
    const marketValueOf = (type: AllocationKey) => roundMoney(positions.filter((position) => position.assetType === type).reduce((total, position) => total + position.marketValue, 0));
    const values: Record<AllocationKey, number> = { CRYPTO: marketValueOf('CRYPTO'), STOCK: marketValueOf('STOCK') };
    const total = values.CRYPTO + values.STOCK;

    return (Object.keys(values) as AllocationKey[]).map((key) => ({
        key,
        label: ALLOCATION_LABELS[key],
        value: values[key],
        percent: total > 0 ? (values[key] / total) * 100 : 0
    }));
}
