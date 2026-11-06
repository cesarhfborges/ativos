/** Preço vindo da última cotação do sistema, e não digitado pelo usuário. */
export interface PriceSuggestion {
    symbol: string;
    price: number;
}

/** Uma cotação zerada não serve de sugestão: o formulário recusaria o valor. */
export function suggestionFor(symbol: string, price: number | null | undefined): PriceSuggestion | null {
    return price ? { symbol, price } : null;
}

/** O que o usuário digitou nunca é trocado: só um campo vazio ou com a sugestão anterior pode receber outra. */
export function canSuggestPrice(current: number | null, previous: PriceSuggestion | null): boolean {
    return current === null || current === previous?.price;
}

/** O valor continua sendo a sugestão daquele ativo? Se o usuário alterou o campo, a dica some. */
export function isSuggestedPrice(current: number | null, symbol: string | null | undefined, suggestion: PriceSuggestion | null): boolean {
    return !!suggestion && current === suggestion.price && symbol?.trim().toUpperCase() === suggestion.symbol;
}
