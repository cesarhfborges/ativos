package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.assetmanagement.entity.AssetType;

/** {@code name} é usado só no primeiro cadastro do ativo; {@code acceptUnverified} aceita um símbolo que o provedor não encontre. */
public record WatchlistItemOrder(String symbol, String name, AssetType assetType, WatchlistPlan plan, boolean acceptUnverified) {
}
