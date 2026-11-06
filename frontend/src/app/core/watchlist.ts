import { WatchlistItem } from './api.models';

export type PlanKind = 'STOP' | 'SELL' | 'BUY';

export interface PlanTarget {
    kind: PlanKind;
    label: string;
    price: number;
    /** Venda e stop só dizem respeito a quem tem o ativo; sem posição, ficam guardados, sem aviso. */
    active: boolean;
    reached: boolean;
    /** Quanto o preço ainda precisa andar até o alvo; nulo sem cotação ou depois de atingido. */
    distancePercent: number | null;
}

export interface ReachedTarget {
    item: WatchlistItem;
    target: PlanTarget;
}

/** O risco primeiro: um stop atingido pede atenção antes de uma oportunidade. */
const ORDER: PlanKind[] = ['STOP', 'SELL', 'BUY'];

const LABELS: Record<PlanKind, string> = {
    STOP: 'Stop',
    SELL: 'Vender acima de',
    BUY: 'Comprar abaixo de'
};

export const isHeld = (item: Pick<WatchlistItem, 'quantity'>): boolean => item.quantity > 0;

function target(item: WatchlistItem, kind: PlanKind, price: number | null): PlanTarget[] {
    if (price === null) {
        return [];
    }
    const current = item.currentPrice;
    const active = kind === 'BUY' || isHeld(item);
    const touched = current !== null && (kind === 'SELL' ? current >= price : current <= price);
    const reached = active && touched;
    return [
        {
            kind,
            label: LABELS[kind],
            price,
            active,
            reached,
            distancePercent: current !== null && current > 0 && !touched ? (Math.abs(price - current) / current) * 100 : null
        }
    ];
}

/** Os preços definidos no plano, do mais urgente ao menos urgente. */
export function planTargets(item: WatchlistItem): PlanTarget[] {
    return [...target(item, 'STOP', item.stopLoss), ...target(item, 'SELL', item.sellAbove), ...target(item, 'BUY', item.buyBelow)];
}

export function reachedTargets(items: WatchlistItem[]): ReachedTarget[] {
    return items
        .flatMap((item) => planTargets(item).map((plan) => ({ item, target: plan })))
        .filter(({ target: plan }) => plan.reached)
        .sort((a, b) => ORDER.indexOf(a.target.kind) - ORDER.indexOf(b.target.kind) || a.item.symbol.localeCompare(b.item.symbol));
}
