package br.com.chfb.ativo.sharedkernel.market;

import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;

/** Confere no provedor de cotações se um símbolo existe, sem expor qual provedor está ativo. */
public interface SymbolVerifier {

    SymbolCheck verify(String symbol, AssetClass assetClass);
}
