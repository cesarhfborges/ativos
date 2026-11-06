package br.com.chfb.ativo.sharedkernel.market;

import java.util.List;

/** {@code available} falso não diz nada sobre o ativo: é falha, limite de uso ou provedor desligado. */
public record AssetSearchResult(boolean available, List<AssetCandidate> candidates) {

    public static AssetSearchResult found(List<AssetCandidate> candidates) {
        return new AssetSearchResult(true, candidates);
    }

    public static AssetSearchResult unavailable() {
        return new AssetSearchResult(false, List.of());
    }
}
