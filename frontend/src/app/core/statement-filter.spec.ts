import { defaultStatementFilter, EMPTY_STATEMENT_FILTER, hasActiveFilter, isValidFilter, statementParams } from './statement-filter';

describe('statement-filter', () => {
    it('não envia o que não foi preenchido', () => {
        expect(statementParams(EMPTY_STATEMENT_FILTER)).toEqual({});
        expect(statementParams({ ...EMPTY_STATEMENT_FILTER, asset: '   ' })).toEqual({});
    });

    it('envia os filtros preenchidos, inclusive um valor zero', () => {
        expect(statementParams({ ...EMPTY_STATEMENT_FILTER, asset: ' petr ', type: 'SELL', assetType: 'STOCK', status: 'REVERSED', minTotal: 0, maxTotal: 10 })).toEqual({
            asset: 'petr',
            type: 'SELL',
            assetType: 'STOCK',
            status: 'REVERSED',
            minTotal: 0,
            maxTotal: 10
        });
    });

    it('faz as datas valerem do início do primeiro dia ao fim do último, no fuso local', () => {
        const params = statementParams({ ...EMPTY_STATEMENT_FILTER, from: new Date(2026, 2, 1, 15, 30), to: new Date(2026, 2, 15, 8) });

        expect(params['from']).toBe(new Date(2026, 2, 1, 0, 0, 0, 0).toISOString());
        expect(params['to']).toBe(new Date(2026, 2, 15, 23, 59, 59, 999).toISOString());
    });

    it('abre com os últimos 90 dias e nenhum outro filtro', () => {
        const today = new Date(2026, 10, 6, 10);
        const filter = defaultStatementFilter(today);

        expect(filter.to).toEqual(today);
        expect(filter.from).toEqual(new Date(2026, 7, 8, 10));
        expect(filter.asset).toBe('');
        expect(filter.type).toBeNull();
        expect(hasActiveFilter(filter)).toBeTrue();
    });

    it('sem as duas datas, não restringe o período', () => {
        const params = statementParams({ ...defaultStatementFilter(new Date()), from: null, to: null });

        expect(params).toEqual({});
    });

    it('reconhece filtro ativo, mas não texto em branco', () => {
        expect(hasActiveFilter(EMPTY_STATEMENT_FILTER)).toBeFalse();
        expect(hasActiveFilter({ ...EMPTY_STATEMENT_FILTER, asset: '  ' })).toBeFalse();
        expect(hasActiveFilter({ ...EMPTY_STATEMENT_FILTER, asset: 'BTC' })).toBeTrue();
        expect(hasActiveFilter({ ...EMPTY_STATEMENT_FILTER, minTotal: 0 })).toBeTrue();
    });

    it('recusa só o mínimo maior que o máximo', () => {
        expect(isValidFilter(EMPTY_STATEMENT_FILTER)).toBeTrue();
        expect(isValidFilter({ ...EMPTY_STATEMENT_FILTER, minTotal: 10, maxTotal: 10 })).toBeTrue();
        expect(isValidFilter({ ...EMPTY_STATEMENT_FILTER, minTotal: 11, maxTotal: 10 })).toBeFalse();
        expect(isValidFilter({ ...EMPTY_STATEMENT_FILTER, minTotal: 11 })).toBeTrue();
    });
});
