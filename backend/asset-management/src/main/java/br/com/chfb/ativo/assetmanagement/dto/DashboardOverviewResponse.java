package br.com.chfb.ativo.assetmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** {@code quotesUpdatedAt} é nulo enquanto nenhuma posição tiver cotação. */
public record DashboardOverviewResponse(
        BigDecimal investedValue,
        BigDecimal marketValue,
        BigDecimal profitLoss,
        BigDecimal profitLossPercent,
        BigDecimal realizedProfit,
        int positionsWithoutQuote,
        Instant quotesUpdatedAt) {
}
