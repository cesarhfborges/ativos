import { Position, WalletSummary } from './api.models';

export type AllocationKey = 'CRYPTO' | 'STOCK' | 'CASH';

export interface AllocationSlice {
    key: AllocationKey;
    label: string;
    value: number;
    /** Percentual de 0 a 100 sobre o patrimônio. */
    percent: number;
}

const ALLOCATION_LABELS: Record<AllocationKey, string> = {
    CRYPTO: 'Criptomoedas',
    STOCK: 'Ações',
    CASH: 'Caixa'
};

/** Mesma escala e arredondamento (HALF_UP) dos valores monetários no backend. */
export function roundMoney(value: number): number {
    const sign = Math.sign(value);
    return (sign * Math.round((Math.abs(value) + Number.EPSILON) * 1e4)) / 1e4;
}

export function positionCost(position: Position): number {
    return roundMoney(position.quantity * position.averagePrice);
}

export function investedTotal(positions: Position[]): number {
    return roundMoney(positions.reduce((total, position) => total + position.quantity * position.averagePrice, 0));
}

/** Quanto sai do caixa em uma compra. */
export function buyTotal(quantity: number, unitPrice: number, fee: number): number {
    return roundMoney(quantity * unitPrice + fee);
}

/** Quanto entra no caixa em uma venda; negativo quando a taxa supera o valor vendido. */
export function sellNet(quantity: number, unitPrice: number, fee: number): number {
    return roundMoney(quantity * unitPrice - fee);
}

/** Distribuição do patrimônio a preço médio, com as três categorias sempre presentes e na mesma ordem. */
export function allocation(summary: WalletSummary): AllocationSlice[] {
    const investedIn = (type: Position['assetType']) => investedTotal(summary.positions.filter((position) => position.assetType === type));
    const values: Record<AllocationKey, number> = {
        CRYPTO: investedIn('CRYPTO'),
        STOCK: investedIn('STOCK'),
        CASH: summary.fiatBalance
    };
    const total = values.CRYPTO + values.STOCK + values.CASH;

    return (Object.keys(values) as AllocationKey[]).map((key) => ({
        key,
        label: ALLOCATION_LABELS[key],
        value: values[key],
        percent: total > 0 ? (values[key] / total) * 100 : 0
    }));
}
