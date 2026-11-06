import { canSuggestPrice, isSuggestedPrice, suggestionFor } from './price-suggestion';

const petr = { symbol: 'PETR4', price: 40 };

describe('price-suggestion', () => {
    it('sugere só cotações com valor', () => {
        expect(suggestionFor('PETR4', 40)).toEqual(petr);
        expect(suggestionFor('PETR4', null)).toBeNull();
        expect(suggestionFor('PETR4', undefined)).toBeNull();
        expect(suggestionFor('PETR4', 0)).toBeNull();
    });

    it('preenche um campo vazio', () => {
        expect(canSuggestPrice(null, null)).toBeTrue();
    });

    it('troca a sugestão anterior ao mudar de ativo', () => {
        expect(canSuggestPrice(40, petr)).toBeTrue();
    });

    it('nunca troca o que o usuário digitou', () => {
        expect(canSuggestPrice(41, petr)).toBeFalse();
        expect(canSuggestPrice(41, null)).toBeFalse();
    });

    it('reconhece a sugestão, sem diferenciar maiúsculas', () => {
        expect(isSuggestedPrice(40, ' petr4 ', petr)).toBeTrue();
    });

    it('deixa de ser sugestão se o preço ou o ativo mudar', () => {
        expect(isSuggestedPrice(41, 'PETR4', petr)).toBeFalse();
        expect(isSuggestedPrice(40, 'VALE3', petr)).toBeFalse();
        expect(isSuggestedPrice(40, 'PETR4', null)).toBeFalse();
    });
});
