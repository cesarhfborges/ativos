package br.com.chfb.ativo.markettracker.dto;

import br.com.chfb.ativo.markettracker.entity.SyncItemKind;
import br.com.chfb.ativo.markettracker.entity.SyncItemStatus;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;

/** {@code points} só existe na carga de histórico. */
public record SyncItemResponse(
        String symbol, AssetClass assetClass, SyncItemKind kind, SyncItemStatus status, Integer points, String message) {
}
