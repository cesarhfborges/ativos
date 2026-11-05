import { normalizePastedNumber } from './decimal-paste';

describe('normalizePastedNumber', () => {
    it('trata o ponto como decimal quando não forma grupos de milhar', () => {
        expect(normalizePastedNumber('0.5')).toBe('0,5');
        expect(normalizePastedNumber('1234.5678')).toBe('1234,5678');
        expect(normalizePastedNumber(' 10.25 ')).toBe('10,25');
    });

    it('mantém o formato pt-BR', () => {
        expect(normalizePastedNumber('1.234')).toBe('1.234');
        expect(normalizePastedNumber('1.234.567')).toBe('1.234.567');
        expect(normalizePastedNumber('1.234,5')).toBe('1.234,5');
        expect(normalizePastedNumber('0,5')).toBe('0,5');
    });
});
