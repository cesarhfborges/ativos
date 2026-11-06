import { DashboardPosition, MarketAsset } from './api.models';
import { alignIndexed, axisDateFormat, composition, movers24h, periodChanges, portfolioChange24h, resultByAsset, returnSeries } from './market';

const position = (symbol: string, marketValue: number, profitLoss = 0, currentPrice: number | null = 1) => ({ symbol, marketValue, profitLoss, currentPrice }) as DashboardPosition;
const asset = (symbol: string, change24hPercent: number | null, periodChangePercent: number | null = null) => ({ symbol, change24hPercent, periodChangePercent, periodLow: null, periodHigh: null, prices: [] }) as MarketAsset;

describe('market', () => {
    it('calcula a variação da carteira em 24 horas ponderada pelo valor, sem os ativos sem referência', () => {
        const change = portfolioChange24h([position('A', 110), position('B', 90), position('C', 1000)], [asset('A', 10), asset('B', -10), asset('C', null)]);
        // Antes: 100 + 100 = 200. Agora: 110 + 90 = 200.
        expect(change?.value).toBeCloseTo(0);
        expect(change?.percent).toBeCloseTo(0);

        const up = portfolioChange24h([position('A', 110)], [asset('A', 10)]);
        expect(up?.value).toBeCloseTo(10);
        expect(up?.percent).toBeCloseTo(10);
    });

    it('não informa a variação da carteira sem nenhuma referência ou sem cotação', () => {
        expect(portfolioChange24h([position('A', 100)], [asset('A', null)])).toBeNull();
        expect(portfolioChange24h([position('A', 100, 0, null)], [asset('A', 5)])).toBeNull();
        expect(portfolioChange24h([], [])).toBeNull();
    });

    it('não chama de alta o que caiu, nem de queda o que subiu', () => {
        expect(movers24h([asset('A', 5), asset('B', -2), asset('C', null)])).toEqual({ up: { symbol: 'A', percent: 5 }, down: { symbol: 'B', percent: -2 } });
        expect(movers24h([asset('A', -1), asset('B', -2)]).up).toBeNull();
        expect(movers24h([asset('A', 1), asset('B', 0)]).down).toBeNull();
        expect(movers24h([])).toEqual({ up: null, down: null });
    });

    it('ordena a composição e soma a cauda em Outros, mantendo 100%', () => {
        const positions = ['A', 'B', 'C', 'D'].map((symbol, index) => position(symbol, index + 1));
        const items = composition(positions, 3);
        expect(items.map((item) => item.label)).toEqual(['D', 'C', 'Outros']);
        expect(items[2].value).toBeCloseTo(30);
        expect(items.reduce((sum, item) => sum + item.value, 0)).toBeCloseTo(100);
        expect(composition([])).toEqual([]);
    });

    it('não mostra resultado de quem não tem cotação', () => {
        expect(resultByAsset([position('A', 1, -5), position('B', 1, 7), position('C', 1, 0, null)])).toEqual([
            { label: 'B', value: 7 },
            { label: 'A', value: -5 }
        ]);
    });

    it('ordena a variação do período e ignora quem não tem dados', () => {
        expect(periodChanges([asset('A', null, -3), asset('B', null, 8), asset('C', null, null)]).map((item) => item.label)).toEqual(['B', 'A']);
    });

    it('calcula o retorno sobre o investido e ignora pontos sem posição', () => {
        const points = [
            { time: 't0', investedBalance: 0, totalBalance: 0 },
            { time: 't1', investedBalance: 200, totalBalance: 250 }
        ];
        expect(returnSeries(points)).toEqual([{ time: 't1', percent: 25 }]);
    });

    it('só mostra a hora no eixo em períodos curtos', () => {
        expect(axisDateFormat(['2026-01-01T00:00:00Z', '2026-01-08T00:00:00Z'])).toBe('dd/MM HH:mm');
        expect(axisDateFormat(['2026-01-01T00:00:00Z', '2026-01-09T00:00:00Z'])).toBe('dd/MM/yy');
        expect(axisDateFormat([])).toBe('dd/MM HH:mm');
    });

    it('alinha os ativos por intervalo e indexa cada um pelo próprio início', () => {
        const aligned = alignIndexed(
            [
                {
                    symbol: 'CRIPTO',
                    prices: [
                        { time: '2026-01-01T23:00', price: 50 },
                        { time: '2026-01-02T23:00', price: 75 }
                    ]
                },
                { symbol: 'ACAO', prices: [{ time: '2026-01-02T20:00', price: 10 }] }
            ],
            (time) => time.slice(0, 10)
        );
        expect(aligned.times).toEqual(['2026-01-01T23:00', '2026-01-02T23:00']);
        expect(aligned.series[0].values).toEqual([100, 150]);
        expect(aligned.series[1].values).toEqual([null, 100]);
    });
});
