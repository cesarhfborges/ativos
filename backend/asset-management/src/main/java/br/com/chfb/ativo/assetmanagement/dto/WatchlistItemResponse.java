package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.TechnicalSignal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sem posição aberta, {@code quantity} é zero e {@code averagePrice} é nulo. Sem cotação, os dados de mercado são nulos.
 * {@code signal} é nulo com o módulo de projeções desligado.
 */
public record WatchlistItemResponse(
        UUID id,
        String symbol,
        String name,
        AssetType assetType,
        BigDecimal buyBelow,
        BigDecimal sellAbove,
        BigDecimal stopLoss,
        String note,
        BigDecimal currentPrice,
        Instant quoteUpdatedAt,
        BigDecimal change24hPercent,
        BigDecimal periodChangePercent,
        BigDecimal periodLow,
        BigDecimal periodHigh,
        List<PricePoint> prices,
        BigDecimal quantity,
        BigDecimal averagePrice,
        Instant createdAt,
        TechnicalSignal signal) {
}
