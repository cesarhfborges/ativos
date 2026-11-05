import { Position } from './api.models';
import { allocation, buyTotal, investedTotal, positionCost, roundMoney, sellNet } from './portfolio';

const btc: Position = { symbol: 'BTC', name: 'Bitcoin', assetType: 'CRYPTO', quantity: 0.5, averagePrice: 100000 };
const petr: Position = { symbol: 'PETR4', name: 'Petrobras', assetType: 'STOCK', quantity: 100, averagePrice: 30.25 };

describe('portfolio', () => {
    describe('roundMoney', () => {
        it('arredonda para quatro casas, com meio para cima', () => {
            expect(roundMoney(1.00005)).toBe(1.0001);
            expect(roundMoney(1.00004)).toBe(1);
        });

        it('elimina o ruído do ponto flutuante', () => {
            expect(0.1 * 3).not.toBe(0.3);
            expect(roundMoney(0.1 * 3)).toBe(0.3);
        });

        it('trata o negativo pelo valor absoluto, como o HALF_UP do BigDecimal', () => {
            expect(roundMoney(-1.00005)).toBe(-1.0001);
        });
    });

    it('calcula o custo da posição e o total investido a preço médio', () => {
        expect(positionCost(btc)).toBe(50000);
        expect(investedTotal([btc, petr])).toBe(53025);
        expect(investedTotal([])).toBe(0);
    });

    it('soma a taxa na compra e desconta na venda', () => {
        expect(buyTotal(0.5, 100, 1.5)).toBe(51.5);
        expect(sellNet(0.25, 120, 1.5)).toBe(28.5);
        expect(sellNet(1, 1, 2)).toBe(-1);
    });

    describe('allocation', () => {
        it('reparte o patrimônio entre cripto, ações e caixa', () => {
            const slices = allocation({ fiatBalance: 46975, positions: [btc, petr] });

            expect(slices.map((slice) => slice.key)).toEqual(['CRYPTO', 'STOCK', 'CASH']);
            expect(slices.map((slice) => slice.value)).toEqual([50000, 3025, 46975]);
            expect(slices.map((slice) => slice.percent)).toEqual([50, 3.025, 46.975]);
        });

        it('mantém as categorias e não divide por zero em carteira vazia', () => {
            const slices = allocation({ fiatBalance: 0, positions: [] });

            expect(slices.length).toBe(3);
            expect(slices.every((slice) => slice.percent === 0)).toBeTrue();
        });
    });
});
