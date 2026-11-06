package br.com.chfb.ativo.assetmanagement.exception;

import br.com.chfb.ativo.assetmanagement.entity.AssetType;

/** {@code suggestedType} é o tipo em que o símbolo foi encontrado, se foi. */
public class SymbolNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final AssetType suggestedType;

    public SymbolNotFoundException(String symbol, AssetType suggestedType) {
        super(suggestedType == null
                ? "Não encontramos o símbolo " + symbol + " no provedor de cotações"
                : "O símbolo " + symbol + " existe como " + (suggestedType == AssetType.CRYPTO ? "criptomoeda" : "ação"));
        this.suggestedType = suggestedType;
    }

    public AssetType getSuggestedType() {
        return suggestedType;
    }
}
