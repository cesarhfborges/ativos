import { DashboardPosition, MarketAsset, WatchlistItem } from './api.models';
import { annualVolatility, portfolioRadar } from './portfolio-radar';

const position = (symbol: string, marketValue: number, investedValue = marketValue, currentPrice: number | null = 1) => ({ symbol, marketValue, investedValue, profitLoss: marketValue - investedValue, currentPrice }) as DashboardPosition;
const axis = (axes: ReturnType<typeof portfolioRadar>, key: string) => axes.find((item) => item.key === key)!;
const daily = (prices: number[]) => prices.map((price, day) => ({ time: new Date(Date.UTC(2026, 0, day + 1)).toISOString(), price }));
const asset = (symbol: string, periodChangePercent: number | null, prices: number[] = []) => ({ symbol, periodChangePercent, prices: daily(prices) }) as MarketAsset;
/** Preços diários que cobrem o período inteiro. */
const covering = (symbol: string, periodChangePercent: number | null, days = 30) => asset(symbol, periodChangePercent, Array.from({ length: days + 1 }, () => 10));
const radar = (positions: DashboardPosition[], market: MarketAsset[] = [], watchlist: WatchlistItem[] | null = [], days = 30) => portfolioRadar(positions, market, watchlist, days, `${days} dias`);

describe('portfolio-radar', () => {
    it('sem posições, nenhuma nota é inventada', () => {
        expect(radar([]).every((item) => item.score === null)).toBeTrue();
    });

    it('um único ativo não tem diversificação', () => {
        expect(axis(radar([position('A', 100)]), 'diversification').score).toBe(0);
    });

    it('dez ativos de mesmo peso têm diversificação completa, e mais que isso não passa do máximo', () => {
        const assets = (count: number) => Array.from({ length: count }, (_, index) => position(`A${index}`, 100));
        expect(axis(radar(assets(10)), 'diversification').score).toBeCloseTo(100);
        expect(axis(radar(assets(20)), 'diversification').score).toBe(100);
    });

    it('cada ativo novo de mesmo peso ajuda menos que o anterior', () => {
        const score = (count: number) => axis(radar(Array.from({ length: count }, (_, index) => position(`A${index}`, 100))), 'diversification').score!;
        expect(score(2)).toBeCloseTo(55.6, 1);
        expect(score(3)).toBeCloseTo(74.1, 1);
        expect(score(5)).toBeCloseTo(88.9, 1);
    });

    it('dois ativos desiguais ficam na metade da escala, e o detalhe mostra os ativos equivalentes', () => {
        const result = axis(radar([position('XRP', 616), position('BBSE3', 384)]), 'diversification');
        expect(result.score).toBeCloseTo(52.6, 1);
        expect(result.detail).toContain('1,9 ativos');
    });

    it('a concentração pesa mesmo com muitos ativos', () => {
        const concentrated = [position('A', 910), ...Array.from({ length: 9 }, (_, index) => position(`B${index}`, 10))];
        expect(axis(radar(concentrated), 'diversification').score!).toBeLessThan(20);
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
        const axes = radar([position('A', 300), position('B', 100), position('C', 100)], [covering('A', 20), covering('B', -20), covering('C', null)]);
        // (300 × 20 − 100 × 20) / 400 = +10%, que fica a 3/4 da escala de -20 a +20.
        expect(axis(axes, 'momentum').score).toBeCloseTo(75);
        expect(axis(axes, 'momentum').detail).toContain('em 2 de 3 posições');
    });

    it('a escala do momento acompanha o período', () => {
        const score = (days: number, change: number) => axis(radar([position('A', 100)], [covering('A', change, days)], [], days), 'momentum').score!;
        expect(score(30, 20)).toBeCloseTo(100);
        // A mesma alta de 20% é o máximo em 30 dias, mas só parte da escala em 120 dias, que vai até 40%.
        expect(score(120, 20)).toBeCloseTo(75);
        expect(score(365, -47)).toBeGreaterThan(0);
    });

    it('não soma ao período quem só tem histórico de parte dele', () => {
        // Em 1 ano: uma moeda com o ano inteiro e uma ação com 88 dias, a mesma situação de XRP e BBSE3.
        const axes = radar([position('XRP', 616), position('BBSE3', 384)], [covering('XRP', -47, 364), covering('BBSE3', -1, 88)], [], 365);
        const range = 20 * Math.sqrt(365 / 30);
        expect(axis(axes, 'momentum').score).toBeCloseTo(((-47 + range) / (2 * range)) * 100);
        expect(axis(axes, 'momentum').detail).toContain('em 1 de 2 posições');
    });

    it('um fim de semana no início de 7 dias não conta como histórico curto', () => {
        const axes = radar([position('A', 100)], [covering('A', 5, 5)], [], 7);
        expect(axis(axes, 'momentum').score).not.toBeNull();
    });

    it('sem cobrir metade do valor da carteira, o momento fica sem nota', () => {
        const axes = radar([position('A', 100), position('B', 300)], [covering('A', 10, 364), covering('B', 10, 88)], [], 365);
        expect(axis(axes, 'momentum').score).toBeNull();
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
