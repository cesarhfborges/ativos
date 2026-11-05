import { DashboardPosition } from './api.models';
import { allocation, averageAfterBuy, buyTotal, roundMoney, saleResult, sellNet } from './portfolio';

const position = (assetType: DashboardPosition['assetType'], quantity: number, averagePrice: number, marketValue: number) => ({ assetType, quantity, averagePrice, marketValue }) as DashboardPosition;

describe('portfolio', () => {
    it('arredonda como o HALF_UP do BigDecimal e elimina o ruído do ponto flutuante', () => {
        expect(roundMoney(1.00005)).toBe(1.0001);
        expect(roundMoney(-1.00005)).toBe(-1.0001);
        expect(roundMoney(0.1 * 3)).toBe(0.3);
    });

    it('soma a taxa na compra e desconta na venda', () => {
        expect(buyTotal(0.5, 100, 1.5)).toBe(51.5);
        expect(sellNet(0.25, 120, 1.5)).toBe(28.5);
    });

    it('calcula o preço médio ponderado e o resultado da venda', () => {
        const held = position('CRYPTO', 1, 100, 0);
        expect(averageAfterBuy(held, 1, 200)).toBe(150);
        expect(saleResult(held, 1, 120, 5)).toBe(15);
    });

    it('reparte o patrimônio a mercado e não divide por zero em carteira vazia', () => {
        const slices = allocation([position('CRYPTO', 1, 10, 30), position('STOCK', 1, 10, 70)]);
        expect(slices.map((slice) => slice.percent)).toEqual([30, 70]);
        expect(allocation([]).every((slice) => slice.percent === 0)).toBeTrue();
    });
});
