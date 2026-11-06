package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.assetmanagement.entity.Asset;
import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import java.math.BigDecimal;

/** {@code price} é a última cotação gravada, nula se o ativo nunca foi cotado. */
public record AssetResponse(String symbol, String name, AssetType assetType, BigDecimal price) {

    public static AssetResponse from(Asset asset, BigDecimal price) {
        return new AssetResponse(asset.getSymbol(), asset.getName(), asset.getAssetType(), price);
    }
}
