import { WatchlistItem } from './api.models';
import { isHeld, planTargets, reachedTargets } from './watchlist';

const item = (overrides: Partial<WatchlistItem> = {}): WatchlistItem => ({
    id: '1',
    symbol: 'PETR4',
    name: 'Petrobras',
    assetType: 'STOCK',
    buyBelow: null,
    sellAbove: null,
    stopLoss: null,
    note: null,
    currentPrice: 40,
    quoteUpdatedAt: null,
    change24hPercent: null,
    periodChangePercent: null,
    periodLow: null,
    periodHigh: null,
    prices: [],
    quantity: 0,
    averagePrice: null,
    createdAt: '2026-11-06T00:00:00Z',
    signal: null,
    ...overrides
});

describe('watchlist', () => {
    it('não tem alvos sem preços definidos', () => {
        expect(planTargets(item())).toEqual([]);
    });

    it('calcula quanto falta até o alvo a partir do preço atual', () => {
        const [buy] = planTargets(item({ buyBelow: 30 }));
        expect(buy.reached).toBeFalse();
        expect(buy.distancePercent).toBeCloseTo(25);
    });

    it('atinge a compra no preço ou abaixo dele', () => {
        expect(planTargets(item({ buyBelow: 40 }))[0].reached).toBeTrue();
        expect(planTargets(item({ buyBelow: 50 }))[0].reached).toBeTrue();
        expect(planTargets(item({ buyBelow: 39.99 }))[0].reached).toBeFalse();
    });

    it('venda e stop só avisam quem tem o ativo', () => {
        const watching = planTargets(item({ sellAbove: 30, stopLoss: 50 }));
        expect(watching.every((plan) => !plan.active && !plan.reached)).toBeTrue();

        const holding = planTargets(item({ sellAbove: 30, stopLoss: 50, quantity: 1 }));
        expect(holding.map((plan) => [plan.kind, plan.reached])).toEqual([
            ['STOP', true],
            ['SELL', true]
        ]);
    });

    it('sem posição, venda e stop tocados não mostram distância enganosa', () => {
        const [sell] = planTargets(item({ sellAbove: 30 }));
        expect(sell.distancePercent).toBeNull();
    });

    it('sem cotação, nenhum alvo é atingido', () => {
        const [buy] = planTargets(item({ buyBelow: 30, currentPrice: null }));
        expect(buy.reached).toBeFalse();
        expect(buy.distancePercent).toBeNull();
    });

    it('lista os alvos atingidos com o risco primeiro', () => {
        const items = [item({ symbol: 'B', buyBelow: 50 }), item({ symbol: 'A', sellAbove: 30, quantity: 1 }), item({ symbol: 'C', stopLoss: 50, quantity: 1 })];
        expect(reachedTargets(items).map(({ item: reached, target }) => `${reached.symbol}:${target.kind}`)).toEqual(['C:STOP', 'A:SELL', 'B:BUY']);
    });

    it('identifica quem tem posição', () => {
        expect(isHeld(item())).toBeFalse();
        expect(isHeld(item({ quantity: 0.5 }))).toBeTrue();
    });
});
