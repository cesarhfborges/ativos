package br.com.chfb.ativo.assetmanagement.entity;

import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;

public enum AssetType {
    STOCK,
    CRYPTO;

    /** A forma como os outros módulos enxergam o tipo. */
    public AssetClass toAssetClass() {
        return this == CRYPTO ? AssetClass.CRYPTO : AssetClass.STOCK;
    }
}
