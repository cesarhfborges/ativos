package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.assetmanagement.entity.Asset;
import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import br.com.chfb.ativo.assetmanagement.entity.VerificationStatus;
import java.util.UUID;

/** Ativo do catálogo na visão do admin: mostra quantos usam, nunca quem. */
public record AssetCatalogEntry(
        UUID id,
        String symbol,
        String name,
        AssetType assetType,
        VerificationStatus verificationStatus,
        long openPositions,
        long transactions) {

    public static AssetCatalogEntry from(Asset asset, long openPositions, long transactions) {
        return new AssetCatalogEntry(
                asset.getId(),
                asset.getSymbol(),
                asset.getName(),
                asset.getAssetType(),
                asset.getVerificationStatus(),
                openPositions,
                transactions);
    }
}
