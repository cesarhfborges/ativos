package br.com.chfb.ativo.assetmanagement.dto;

import java.util.List;

public record AssetCatalogPage(List<AssetCatalogEntry> items, long total) {
}
