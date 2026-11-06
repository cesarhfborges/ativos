package br.com.chfb.ativo.sharedkernel.market;

import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;

/** Procura ativos no provedor de cotações, inclusive os que ainda não estão no catálogo. */
public interface AssetSearch {

    AssetSearchResult search(String query, AssetClass assetClass);
}
