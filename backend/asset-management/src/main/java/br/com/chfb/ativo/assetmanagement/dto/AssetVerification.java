package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.sharedkernel.market.SymbolCheck;

/** O ativo depois da conferência e o que o provedor respondeu. */
public record AssetVerification(AssetCatalogEntry asset, SymbolCheck check) {
}
