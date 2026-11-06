import { DashboardPosition, MarketAsset, WatchlistItem } from './api.models';
import { annualVolatility, portfolioRadar } from './portfolio-radar';

const position = (symbol: string, marketValue: number, investedValue = marketValue, currentPrice: number | null = 1) => ({ symbol, marketValue, investedValue, profitLoss: marketValue - investedValue, currentPrice }) as DashboardPosition;
const axis = (axes: ReturnType<typeof portfolioRadar>, key: string) => axes.find((item) => item.key === key)!;
const daily = (prices: number[]) => prices.map((price, day) => ({ time: new Date(Date.UTC(2026, 0, day + 1)).toISOString(), price }));
const asset = (symbol: string, periodChangePercent: number | null, prices: number[] = []) => ({ symbol, periodChangePercent, prices: daily(prices) }) as MarketAsset;
const radar = (positions: DashboardPosition[], market: MarketAsset[] = [], watchlist: WatchlistItem[] | null = []) => portfolioRadar(positions, market, watchlist, '30 dias');

describe('portfolio-radar', () => {
    it('sem posições, nenhuma nota é inventada', () => {
        expect(radar([]).every((item) => item.score === null)).toBeTrue();
    });

    it('um único ativo não tem diversificação', () => {
        expect(axis(radar([position('A', 100)]), 'diversification').score).toBe(0);
    });

    it('dez ativos de mesmo peso têm diversificação completa, e o peso importa', () => {
        const even = Array.from({ length: 10 }, (_, index) => position(`A${index}`, 100));
        expect(axis(radar(even), 'diversification').score).toBeCloseTo(100);
        const concentrated = [position('A', 910), ...Array.from({ length: 9 }, (_, index) => position(`B${index}`, 10))];
        expect(axis(radar(concentrated), 'diversification').score!).toBeLessThan(5);
    });

    it('rentabilidade zero fica no meio e os extremos são limitados', () => {
        expect(axis(radar([position('A', 100)]), 'profitability').score).toBe(50);
        expect(axis(radar([position('A', 150, 100)]), 'profitability').score).toBe(100);
        expect(axis(radar([position('A', 1500, 100)]), 'profitability').score).toBe(100);
        expect(axis(radar([position('A', 10, 100)]), 'profitability').score).toBe(0);
    });

    it('rentabilidade ignora posições sem cotação', () => {
        expect(axis(radar([position('A', 100, 100, null)]), 'profitability').score).toBeNull();
    });

    it('momento pondera a variação pelo valor e ignora quem não tem dado', () => {
        const axes = radar([position('A', 300), position('B', 100), position('C', 500)], [asset('A', 20), asset('B', -20), asset('C', null)]);
        // (300 × 20 − 100 × 20) / 400 = +10%, que fica a 3/4 da escala de -20 a +20.
        expect(axis(axes, 'momentum').score).toBeCloseTo(75);
    });

    it('preço constante tem estabilidade máxima, e poucos pontos não geram nota', () => {
        expect(axis(radar([position('A', 100)], [asset('A', 0, [10, 10, 10, 10, 10, 10, 10])]), 'stability').score).toBe(100);
        expect(axis(radar([position('A', 100)], [asset('A', 0, [10, 11, 12])]), 'stability').score).toBeNull();
    });

    it('oscilar mais reduz a estabilidade', () => {
        const calm = axis(radar([position('A', 100)], [asset('A', 0, [100, 101, 100, 101, 100, 101, 100])]), 'stability').score!;
        const wild = axis(radar([position('A', 100)], [asset('A', 0, [100, 110, 100, 110, 100, 110, 100])]), 'stability').score!;
        expect(wild).toBeLessThan(calm);
    });

    it('anualiza a volatilidade pelo intervalo real dos pontos', () => {
        const prices = [100, 101, 100, 101, 100, 101, 100];
        const volatility = annualVolatility(daily(prices))!;
        // Desvio diário de cerca de 1% vezes a raiz de 365 dias.
        expect(volatility).toBeGreaterThan(15);
        expect(volatility).toBeLessThan(25);
    });

    it('conta as posições no lucro', () => {
        expect(axis(radar([position('A', 120, 100), position('B', 90, 100)]), 'winners').score).toBe(50);
    });

    it('plano de saída considera só venda e stop de ativos em carteira', () => {
        const watchlist = [
            { symbol: 'A', sellAbove: 10, stopLoss: null },
            { symbol: 'B', sellAbove: null, stopLoss: null },
            { symbol: 'Z', sellAbove: 10, stopLoss: 5 }
        ] as WatchlistItem[];
        expect(axis(radar([position('A', 100), position('B', 100)], [], watchlist), 'planning').score).toBe(50);
    });

    it('sem conseguir ler o acompanhamento, o plano fica sem nota', () => {
        expect(axis(radar([position('A', 100)], [], null), 'planning').score).toBeNull();
    });
});
