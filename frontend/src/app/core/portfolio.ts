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

/**
 * Quantidade comprada com um valor que já inclui a taxa, em oito casas como no backend. Arredonda para baixo,
 * para o total nunca passar do valor pago; o `toFixed` tira o ruído que faria 0,29 / 0,01 virar 28,99999999.
 */
export function quantityForAmount(amount: number, unitPrice: number, fee: number): number {
    if (unitPrice <= 0 || amount <= fee) {
        return 0;
    }
    return Math.floor(Number((((amount - fee) / unitPrice) * 1e8).toFixed(3))) / 1e8;
}

/** Valor líquido de uma venda; negativo quando a taxa supera o valor vendido. */
export function sellNet(quantity: number, unitPrice: number, fee: number): number {
    return roundMoney(quantity * unitPrice - fee);
}

/** Mesma escala e arredondamento dos preços no backend, com oito casas. */
export function roundPrice(value: number): number {
    return Math.round((value + Number.EPSILON) * 1e8) / 1e8;
}

/** Preço médio ponderado depois da compra; a taxa faz parte do custo, como no backend. */
export function averageAfterBuy(position: Position, quantity: number, unitPrice: number, fee: number): number {
    return roundPrice((position.quantity * position.averagePrice + quantity * unitPrice + fee) / (position.quantity + quantity));
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
