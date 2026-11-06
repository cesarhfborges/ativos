package br.com.chfb.ativo.sharedkernel.portfolio;

import java.math.BigDecimal;

/** Quanto da carteira está em um ativo, a valor de mercado. */
public record AssetExposure(String symbol, AssetClass assetClass, BigDecimal marketValue) {
}
