package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import java.math.BigDecimal;
import java.time.Instant;

/** Sem cotação, {@code currentPrice} é nulo e o valor de mercado usa o preço médio, com lucro zero. */
public record DashboardPositionResponse(
        String symbol,
        String name,
        AssetType assetType,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        BigDecimal investedValue,
        BigDecimal marketValue,
        BigDecimal profitLoss,
        BigDecimal profitLossPercent,
        Instant quoteUpdatedAt) {
}
