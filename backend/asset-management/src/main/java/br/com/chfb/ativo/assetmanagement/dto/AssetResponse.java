package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.assetmanagement.entity.Asset;
import br.com.chfb.ativo.assetmanagement.entity.AssetType;

public record AssetResponse(String symbol, String name, AssetType assetType) {

    public static AssetResponse from(Asset asset) {
        return new AssetResponse(asset.getSymbol(), asset.getName(), asset.getAssetType());
    }
}
