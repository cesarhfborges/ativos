package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.assetmanagement.service.PortfolioValuation;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.Quote;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Campos ficam nulos quando não há preços suficientes para calculá-los. */
public record MarketAssetResponse(
        String symbol,
        BigDecimal change24hPercent,
        BigDecimal periodChangePercent,
        BigDecimal periodLow,
        BigDecimal periodHigh,
        List<PricePoint> prices) {

    /** A última cotação fecha a série, para o gráfico e a variação terminarem no preço mostrado na tabela. */
    public static MarketAssetResponse of(String symbol, Quote quote, BigDecimal price24hAgo, List<PricePoint> history) {
        List<PricePoint> prices = new ArrayList<>(history);
        if (quote != null && (prices.isEmpty() || quote.lastUpdatedAt().isAfter(prices.get(prices.size() - 1).time()))) {
            prices.add(new PricePoint(quote.lastUpdatedAt(), quote.price()));
        }
        BigDecimal first = prices.isEmpty() ? null : prices.get(0).price();
        BigDecimal last = prices.isEmpty() ? null : prices.get(prices.size() - 1).price();
        return new MarketAssetResponse(
                symbol,
                quote == null ? null : change(price24hAgo, quote.price()),
                prices.size() < 2 ? null : change(first, last),
                prices.stream().map(PricePoint::price).min(Comparator.naturalOrder()).orElse(null),
                prices.stream().map(PricePoint::price).max(Comparator.naturalOrder()).orElse(null),
                prices);
    }

    private static BigDecimal change(BigDecimal from, BigDecimal to) {
        if (from == null || from.signum() == 0) {
            return null;
        }
        return PortfolioValuation.percent(to.subtract(from), from);
    }
}
