package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import br.com.chfb.ativo.assetmanagement.entity.WalletPosition;
import java.math.BigDecimal;

public record PositionResponse(
        String symbol, String name, AssetType assetType, BigDecimal quantity, BigDecimal averagePrice) {

    public static PositionResponse from(WalletPosition position) {
        return new PositionResponse(
                position.getAsset().getSymbol(),
                position.getAsset().getName(),
                position.getAsset().getAssetType(),
                position.getQuantity(),
                position.getAveragePrice());
    }
}
