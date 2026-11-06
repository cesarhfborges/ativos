import { TechnicalSignal } from './api.models';
import { signalReading } from './technical-signal';

const signal = (overrides: Partial<TechnicalSignal> = {}): TechnicalSignal => ({
    available: true,
    momentum: 'NEUTRAL',
    trend: 'UP',
    rsi: 50,
    shortAverage: 11,
    longAverage: 10,
    oversoldLimit: 30,
    overboughtLimit: 70,
    closes: 60,
    requiredCloses: 50,
    asOf: '2026-11-06T12:00:00Z',
    ...overrides
});

describe('technical-signal', () => {
    it('neutro mostra o RSI, os limites e a tendência', () => {
        const reading = signalReading(signal({ rsi: 52.34 }));
        expect(reading.label).toBe('Neutro');
        expect(reading.reason).toContain('RSI 52,3');
        expect(reading.reason).toContain('entre 30 e 70');
        expect(reading.reason).toContain('tendência de alta');
    });

    it('sobrevendido em baixa avisa que pode continuar caindo', () => {
        const reading = signalReading(signal({ momentum: 'OVERSOLD', trend: 'DOWN', rsi: 25 }));
        expect(reading.label).toBe('Sobrevendido');
        expect(reading.reason).toContain('tendência de baixa');
        expect(reading.reason).toContain('Pode continuar caindo');
    });

    it('sobrevendido em alta é um recuo, não uma queda', () => {
        expect(signalReading(signal({ momentum: 'OVERSOLD', trend: 'UP', rsi: 25 })).reason).toContain('recuo');
    });

    it('sobrecomprado em alta avisa que pode continuar subindo', () => {
        const reading = signalReading(signal({ momentum: 'OVERBOUGHT', trend: 'UP', rsi: 80 }));
        expect(reading.label).toBe('Sobrecomprado');
        expect(reading.reason).toContain('Pode continuar subindo');
    });

    it('sobrecomprado em baixa é um repique', () => {
        expect(signalReading(signal({ momentum: 'OVERBOUGHT', trend: 'DOWN', rsi: 80 })).reason).toContain('repique');
    });

    it('nunca manda comprar ou vender', () => {
        const all = (['OVERSOLD', 'NEUTRAL', 'OVERBOUGHT'] as const).flatMap((momentum) => (['UP', 'DOWN'] as const).map((trend) => signalReading(signal({ momentum, trend, rsi: 40 }))));
        for (const reading of all) {
            expect(`${reading.label} ${reading.reason}`.toLowerCase()).not.toMatch(/compre|venda|comprar|vender/);
        }
    });

    it('sem fechamentos suficientes, diz quantos faltam', () => {
        const reading = signalReading(signal({ available: false, rsi: null, momentum: null, trend: null, closes: 12 }));
        expect(reading.label).toBe('Dados insuficientes');
        expect(reading.reason).toContain('50 fechamentos');
        expect(reading.reason).toContain('há 12');
    });

    it('com preços antigos, explica que eles pararam de atualizar', () => {
        const reading = signalReading(signal({ available: false, rsi: null, momentum: null, trend: null, closes: 60 }));
        expect(reading.label).toBe('Dados insuficientes');
        expect(reading.reason).toContain('não são atualizados');
    });
});
