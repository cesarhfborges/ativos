import { DashboardPosition, MarketAsset, PortfolioHistoryPoint, PricePoint } from './api.models';

export interface PortfolioChange {
    percent: number;
    value: number;
}

export interface Mover {
    symbol: string;
    percent: number;
}

export interface RankedItem {
    label: string;
    value: number;
}

export interface AlignedSeries {
    /** Um instante por intervalo, em ordem. */
    times: string[];
    series: { symbol: string; values: (number | null)[] }[];
}

const OTHERS_LABEL = 'Outros';
const MAX_DAYS_WITH_TIME = 7;
const DAY_MS = 24 * 60 * 60 * 1000;

/** Formato das datas do eixo: em períodos longos, a hora só tira o espaço de que o rótulo precisa. */
export function axisDateFormat(times: string[]): string {
    const first = times[0];
    const last = times[times.length - 1];
    return first && last && Date.parse(last) - Date.parse(first) > MAX_DAYS_WITH_TIME * DAY_MS ? 'dd/MM/yy' : 'dd/MM HH:mm';
}

/**
 * Quanto a carteira atual teria variado em 24 horas, com as quantidades de hoje.
 * Ativos sem preço de 24 horas atrás ficam de fora da conta; sem nenhum, não há o que informar.
 */
export function portfolioChange24h(positions: DashboardPosition[], market: MarketAsset[]): PortfolioChange | null {
    const changes = new Map(market.map((asset) => [asset.symbol, asset.change24hPercent]));
    let current = 0;
    let previous = 0;
    for (const position of positions) {
        const change = changes.get(position.symbol);
        if (position.currentPrice === null || change === null || change === undefined || change <= -100) {
            continue;
        }
        current += position.marketValue;
        previous += position.marketValue / (1 + change / 100);
    }
    return previous > 0 ? { percent: ((current - previous) / previous) * 100, value: current - previous } : null;
}

/** Maior alta e maior queda em 24 horas. Um lado fica nulo quando nenhum ativo subiu, ou nenhum caiu. */
export function movers24h(market: MarketAsset[]): { up: Mover | null; down: Mover | null } {
    const changes = market.flatMap((asset) => (asset.change24hPercent === null ? [] : [{ symbol: asset.symbol, percent: asset.change24hPercent }])).sort((a, b) => b.percent - a.percent);
    const first = changes[0];
    const last = changes[changes.length - 1];
    return { up: first && first.percent > 0 ? first : null, down: last && last.percent < 0 ? last : null };
}

/** Maior posição primeiro, com a cauda somada em "Outros" para a lista não crescer sem limite. */
export function composition(positions: DashboardPosition[], maxItems = 8): RankedItem[] {
    const total = positions.reduce((sum, position) => sum + position.marketValue, 0);
    if (total <= 0) {
        return [];
    }
    const items = positions.map((position) => ({ label: position.symbol, value: (position.marketValue / total) * 100 })).sort((a, b) => b.value - a.value);
    if (items.length <= maxItems) {
        return items;
    }
    const head = items.slice(0, maxItems - 1);
    const tail = items.slice(maxItems - 1).reduce((sum, item) => sum + item.value, 0);
    return [...head, { label: OTHERS_LABEL, value: tail }];
}

/** Lucro ou prejuízo por ativo, do maior ganho à maior perda. Sem cotação, não há resultado para mostrar. */
export function resultByAsset(positions: DashboardPosition[]): RankedItem[] {
    return positions
        .filter((position) => position.currentPrice !== null)
        .map((position) => ({ label: position.symbol, value: position.profitLoss }))
        .sort((a, b) => b.value - a.value);
}

/** Variação de cada ativo no período, da maior alta à maior queda. */
export function periodChanges(market: MarketAsset[]): RankedItem[] {
    return market.flatMap((asset) => (asset.periodChangePercent === null ? [] : [{ label: asset.symbol, value: asset.periodChangePercent }])).sort((a, b) => b.value - a.value);
}

/** Retorno das posições abertas em cada ponto: compras e vendas mudam o patrimônio, mas não este número por si só. */
export function returnSeries(points: PortfolioHistoryPoint[]): { time: string; percent: number }[] {
    return points.filter((point) => point.investedBalance > 0).map((point) => ({ time: point.time, percent: ((point.totalBalance - point.investedBalance) / point.investedBalance) * 100 }));
}

/**
 * Põe os ativos na mesma linha do tempo, com cada um valendo 100 no início da própria série.
 * `bucketOf` agrupa os instantes: bolsa e cripto fecham o dia em horários diferentes.
 * Fica nulo o intervalo sem preço, como o fim de semana de uma ação.
 */
export function alignIndexed(assets: { symbol: string; prices: PricePoint[] }[], bucketOf: (time: string) => string): AlignedSeries {
    const timeOfBucket = new Map<string, string>();
    const byAsset = assets.map((asset) => {
        const prices = new Map<string, number>();
        for (const point of asset.prices) {
            const bucket = bucketOf(point.time);
            prices.set(bucket, point.price);
            if (!timeOfBucket.has(bucket) || point.time > timeOfBucket.get(bucket)!) {
                timeOfBucket.set(bucket, point.time);
            }
        }
        return { symbol: asset.symbol, prices, base: asset.prices.find((point) => point.price > 0)?.price ?? null };
    });

    const buckets = [...timeOfBucket.keys()].sort();
    return {
        times: buckets.map((bucket) => timeOfBucket.get(bucket)!),
        series: byAsset.map(({ symbol, prices, base }) => ({
            symbol,
            values: buckets.map((bucket) => {
                const price = prices.get(bucket);
                return price === undefined || base === null ? null : (price / base) * 100;
            })
        }))
    };
}
