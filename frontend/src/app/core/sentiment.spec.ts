import { SentimentReading } from './api.models';
import { coverageNote, GAUGE_CENTER, gaugeArc, gaugePoint, SENTIMENT_BANDS, sentimentComponents, sentimentDay, sentimentZone } from './sentiment';

describe('sentiment', () => {
    it('classifica os limites de cada faixa', () => {
        expect(sentimentZone(0).label).toBe('Medo extremo');
        expect(sentimentZone(24).label).toBe('Medo extremo');
        expect(sentimentZone(25).label).toBe('Medo');
        expect(sentimentZone(44).label).toBe('Medo');
        expect(sentimentZone(45).label).toBe('Neutro');
        expect(sentimentZone(55).label).toBe('Neutro');
        expect(sentimentZone(56).label).toBe('Ganância');
        expect(sentimentZone(75).label).toBe('Ganância');
        expect(sentimentZone(76).label).toBe('Ganância extrema');
        expect(sentimentZone(100).label).toBe('Ganância extrema');
    });

    it('classifica pela nota arredondada, a mesma que aparece na tela', () => {
        expect(sentimentZone(24.6).label).toBe('Medo');
        expect(sentimentZone(55.4).label).toBe('Neutro');
    });

    it('as faixas de desenho cobrem a escala inteira, sem buracos', () => {
        expect(SENTIMENT_BANDS[0].from).toBe(0);
        expect(SENTIMENT_BANDS.at(-1)!.to).toBe(100);
        SENTIMENT_BANDS.slice(1).forEach((band, index) => expect(band.from).toBe(SENTIMENT_BANDS[index].to));
    });

    it('cada faixa tem a própria cor, e ela muda onde o nome escrito muda', () => {
        expect(new Set(SENTIMENT_BANDS.map((band) => band.tone)).size).toBe(5);
        for (const band of SENTIMENT_BANDS.slice(1)) {
            const before = sentimentZone(band.from - 0.01);
            const after = sentimentZone(band.from);
            expect(before.tone).not.toBe(after.tone);
            expect(after.tone).toBe(band.tone);
        }
    });

    it('posiciona o medidor da esquerda para a direita, passando pelo topo', () => {
        const left = gaugePoint(0, 100);
        const top = gaugePoint(50, 100);
        const right = gaugePoint(100, 100);
        expect(left.x).toBeCloseTo(GAUGE_CENTER.x - 100);
        expect(left.y).toBeCloseTo(GAUGE_CENTER.y);
        expect(top.x).toBeCloseTo(GAUGE_CENTER.x);
        expect(top.y).toBeCloseTo(GAUGE_CENTER.y - 100);
        expect(right.x).toBeCloseTo(GAUGE_CENTER.x + 100);
        expect(right.y).toBeCloseTo(GAUGE_CENTER.y);
    });

    it('não deixa o ponteiro sair da escala', () => {
        expect(gaugePoint(-10, 100)).toEqual(gaugePoint(0, 100));
        expect(gaugePoint(130, 100)).toEqual(gaugePoint(100, 100));
    });

    it('desenha o arco no sentido horário', () => {
        expect(gaugeArc(0, 100, 100)).toBe('M 20.00 120.00 A 100 100 0 0 1 220.00 120.00');
    });

    it('lista os quatro componentes com o valor de cada um', () => {
        const reading: SentimentReading = { day: '2026-11-06', score: 50, rsi: 10, trend: 20, range: 30, volatility: 40, coverage: 1 };
        expect(sentimentComponents(reading).map((item) => item.score)).toEqual([10, 20, 30, 40]);
    });

    it('só avisa da cobertura quando falta parte da carteira', () => {
        expect(coverageNote(1)).toBeNull();
        expect(coverageNote(0.999)).toBeNull();
        expect(coverageNote(0.75)).toContain('75%');
    });

    it('mantém o dia em qualquer fuso', () => {
        expect(sentimentDay('2026-11-06')).toBe('06/11/26');
    });
});
