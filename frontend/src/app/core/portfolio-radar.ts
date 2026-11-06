import { DashboardPosition, MarketAsset, WatchlistItem } from './api.models';
import { SentimentTone } from './chart-theme';
import { shortHistoryDays } from './market';

export interface RadarAxis {
    key: string;
    label: string;
    /** De 0 a 100, com 100 sempre no lado favorável. Nulo quando não há dados para calcular. */
    score: number | null;
    /** O valor real por trás da nota, para ela não parecer mais precisa do que é. */
    detail: string;
    /** Como a nota é medida. */
    hint: string;
}

export interface RadarBand {
    label: string;
    tone: SentimentTone;
    /** Nota inicial da faixa, inclusive. */
    from: number;
}

/** Em ordem crescente. O verde é o tom "greed", e não o mais escuro: este se confunde com o vermelho no daltonismo. */
export const RADAR_BANDS: readonly RadarBand[] = [
    { label: 'Baixa', tone: 'extremeFear', from: 0 },
    { label: 'Média', tone: 'neutral', from: 34 },
    { label: 'Alta', tone: 'greed', from: 67 }
];

/** A nota é arredondada antes, para a cor não contradizer o número mostrado. */
export function radarBand(score: number): RadarBand {
    const rounded = Math.round(score);
    return RADAR_BANDS.filter((band) => rounded >= band.from).at(-1) ?? RADAR_BANDS[0];
}

/** Com esse número de ativos de mesmo peso, a diversificação já é considerada completa. */
const FULL_DIVERSIFICATION_ASSETS = 10;
const RETURN_RANGE = 50;
/** Os extremos do Momento em 30 dias. Variações crescem com a raiz do tempo, então a escala acompanha o período. */
const MOMENTUM_RANGE_30_DAYS = 20;
const MOMENTUM_REFERENCE_DAYS = 30;
/** Abaixo disso, a nota falaria de poucos ativos como se fosse a carteira inteira. */
const MIN_VALUE_COVERAGE = 0.5;
const FULL_VOLATILITY = 100;
/** Com menos variações que isso, o desvio-padrão diz pouco. */
const MIN_RETURNS = 5;
const MS_PER_YEAR = 365.25 * 24 * 60 * 60 * 1000;

const NO_DATA = 'Sem dados suficientes';

const clamp = (value: number) => Math.min(100, Math.max(0, value));
const number = (value: number, digits = 1) => value.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
const signed = (value: number) => `${value > 0 ? '+' : value < 0 ? '−' : ''}${number(Math.abs(value))}%`;
/** Zero fica no meio da escala, e os extremos em -range e +range. */
const centered = (value: number, range: number) => clamp(((value + range) / (2 * range)) * 100);

/** Volatilidade anualizada em %. O número de períodos por ano vem dos próprios pontos, que podem ser por hora ou por dia. */
export function annualVolatility(prices: { time: string; price: number }[]): number | null {
    const valid = prices.filter((point) => point.price > 0);
    const returns = valid.slice(1).map((point, index) => Math.log(point.price / valid[index].price));
    if (returns.length < MIN_RETURNS) {
        return null;
    }
    const span = Date.parse(valid[valid.length - 1].time) - Date.parse(valid[0].time);
    if (span <= 0) {
        return null;
    }
    const mean = returns.reduce((sum, value) => sum + value, 0) / returns.length;
    const variance = returns.reduce((sum, value) => sum + (value - mean) ** 2, 0) / (returns.length - 1);
    return Math.sqrt(variance) * Math.sqrt(returns.length / (span / MS_PER_YEAR)) * 100;
}

/** Média ponderada pelo valor de mercado, só entre os ativos com o dado disponível. */
function weightedAverage(values: { weight: number; value: number | null }[]): number | null {
    const known = values.filter((item): item is { weight: number; value: number } => item.value !== null && item.weight > 0);
    const total = known.reduce((sum, item) => sum + item.weight, 0);
    return total > 0 ? known.reduce((sum, item) => sum + item.value * item.weight, 0) / total : null;
}

function diversification(positions: DashboardPosition[]): RadarAxis {
    const base = { key: 'diversification', label: 'Diversificação', hint: 'Quanto a divisão do patrimônio reduz o risco de depender de um só ativo. Cada ativo novo ajuda menos que o anterior, e 10 de mesmo peso é a nota máxima.' };
    const total = positions.reduce((sum, position) => sum + position.marketValue, 0);
    if (total <= 0) {
        return { ...base, score: null, detail: NO_DATA };
    }
    const concentration = positions.reduce((sum, position) => sum + (position.marketValue / total) ** 2, 0);
    const effective = 1 / concentration;
    return {
        ...base,
        // O risco específico da carteira é proporcional à concentração: cada ativo novo elimina menos que o anterior.
        score: clamp(((1 - concentration) / (1 - 1 / FULL_DIVERSIFICATION_ASSETS)) * 100),
        detail: `Equivale a ${number(effective)} ${effective < 1.05 ? 'ativo' : 'ativos'} de mesmo peso`
    };
}

function profitability(positions: DashboardPosition[]): RadarAxis {
    const base = { key: 'profitability', label: 'Rentabilidade', hint: 'Lucro ou prejuízo das posições abertas sobre o custo. Zero fica na metade da escala.' };
    const quoted = positions.filter((position) => position.currentPrice !== null);
    const invested = quoted.reduce((sum, position) => sum + position.investedValue, 0);
    if (invested <= 0) {
        return { ...base, score: null, detail: NO_DATA };
    }
    const percent = (quoted.reduce((sum, position) => sum + position.profitLoss, 0) / invested) * 100;
    return { ...base, score: centered(percent, RETURN_RANGE), detail: `${signed(percent)} sobre o custo` };
}

/** Sem isso, a média mediria outro período: 3 meses de uma ação não podem somar com 1 ano de uma moeda. */
function coversPeriod(asset: MarketAsset | undefined, days: number): boolean {
    return !!asset && asset.prices.length >= 2 && shortHistoryDays(asset.prices, days) === null;
}

function momentum(positions: DashboardPosition[], market: Map<string, MarketAsset>, days: number, periodLabel: string): RadarAxis {
    const range = MOMENTUM_RANGE_30_DAYS * Math.sqrt(days / MOMENTUM_REFERENCE_DAYS);
    const base = { key: 'momentum', label: 'Momento', hint: `Variação dos preços em ${periodLabel}, ponderada pelo valor, só com ativos que têm preços de quase todo o período. Zero fica na metade da escala, e os extremos são −${number(range, 0)}% e +${number(range, 0)}%.` };
    const measured = positions.filter((position) => coversPeriod(market.get(position.symbol), days) && market.get(position.symbol)?.periodChangePercent != null);
    const total = positions.reduce((sum, position) => sum + position.marketValue, 0);
    const measuredValue = measured.reduce((sum, position) => sum + position.marketValue, 0);
    const percent = weightedAverage(measured.map((position) => ({ weight: position.marketValue, value: market.get(position.symbol)?.periodChangePercent ?? null })));
    if (percent === null || measuredValue / total < MIN_VALUE_COVERAGE) {
        return { ...base, score: null, detail: NO_DATA };
    }
    const partial = measured.length < positions.length ? `, em ${measured.length} de ${positions.length} posições` : '';
    return { ...base, score: centered(percent, range), detail: `${signed(percent)} em ${periodLabel}${partial}` };
}

function stability(positions: DashboardPosition[], market: Map<string, MarketAsset>, periodLabel: string): RadarAxis {
    const base = { key: 'stability', label: 'Estabilidade', hint: `Volatilidade anualizada em ${periodLabel}, ponderada pelo valor. Quanto mais o preço oscila, menor a nota.` };
    const volatility = weightedAverage(positions.map((position) => ({ weight: position.marketValue, value: annualVolatility(market.get(position.symbol)?.prices ?? []) })));
    return volatility === null ? { ...base, score: null, detail: NO_DATA } : { ...base, score: clamp(100 - (volatility / FULL_VOLATILITY) * 100), detail: `Volatilidade de ${number(volatility)}% ao ano` };
}

function winners(positions: DashboardPosition[]): RadarAxis {
    const base = { key: 'winners', label: 'Posições no lucro', hint: 'Parte das posições com cotação que está acima do custo.' };
    const quoted = positions.filter((position) => position.currentPrice !== null);
    if (quoted.length === 0) {
        return { ...base, score: null, detail: NO_DATA };
    }
    const count = quoted.filter((position) => position.profitLoss > 0).length;
    return { ...base, score: (count / quoted.length) * 100, detail: `${count} de ${quoted.length} ${quoted.length === 1 ? 'posição' : 'posições'}` };
}

function planning(positions: DashboardPosition[], watchlist: WatchlistItem[] | null): RadarAxis {
    const base = { key: 'planning', label: 'Plano de saída', hint: 'Parte das posições com alvo de venda ou stop definido no Acompanhamento.' };
    if (watchlist === null || positions.length === 0) {
        return { ...base, score: null, detail: NO_DATA };
    }
    const planned = new Set(watchlist.filter((item) => item.sellAbove !== null || item.stopLoss !== null).map((item) => item.symbol));
    const count = positions.filter((position) => planned.has(position.symbol)).length;
    return { ...base, score: (count / positions.length) * 100, detail: `${count} de ${positions.length} ${positions.length === 1 ? 'posição' : 'posições'}` };
}

/** Notas descritivas da carteira, para comparar aspectos diferentes na mesma escala. Não são recomendação. */
export function portfolioRadar(positions: DashboardPosition[], market: MarketAsset[], watchlist: WatchlistItem[] | null, days: number, periodLabel: string): RadarAxis[] {
    const bySymbol = new Map(market.map((asset) => [asset.symbol, asset]));
    return [diversification(positions), profitability(positions), momentum(positions, bySymbol, days, periodLabel), stability(positions, bySymbol, periodLabel), winners(positions), planning(positions, watchlist)];
}
