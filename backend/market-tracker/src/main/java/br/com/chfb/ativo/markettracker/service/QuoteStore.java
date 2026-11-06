package br.com.chfb.ativo.markettracker.service;

import br.com.chfb.ativo.markettracker.repository.MarketQuoteHistoryRepository;
import br.com.chfb.ativo.markettracker.repository.MarketQuoteRepository;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.PriceResolution;
import br.com.chfb.ativo.sharedkernel.market.Quote;
import br.com.chfb.ativo.sharedkernel.market.QuoteLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class QuoteStore implements QuoteLookup {

    private static final int PRICE_SCALE = 8;

    private final MarketQuoteRepository repository;
    private final MarketQuoteHistoryRepository historyRepository;

    public QuoteStore(MarketQuoteRepository repository, MarketQuoteHistoryRepository historyRepository) {
        this.repository = repository;
        this.historyRepository = historyRepository;
    }

    @Override
    public Map<String, Quote> findBySymbols(Collection<String> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        return repository.findBySymbolIn(symbols).stream()
                .map(quote -> new Quote(quote.getSymbol(), quote.getPrice(), quote.getLastUpdatedAt()))
                .collect(Collectors.toMap(Quote::symbol, quote -> quote));
    }

    @Override
    public Map<String, List<PricePoint>> findPriceHistory(Collection<String> symbols, Instant from, PriceResolution resolution) {
        return symbols.isEmpty() ? Map.of() : historyRepository.findHistory(symbols, from, resolution);
    }

    @Override
    public Map<String, BigDecimal> findPricesAt(Collection<String> symbols, Instant at) {
        return symbols.isEmpty() ? Map.of() : historyRepository.findPricesAt(symbols, at);
    }

    @Override
    public Map<String, List<PricePoint>> findPricesBetween(Collection<String> symbols, Instant from, Instant to) {
        return symbols.isEmpty() ? Map.of() : historyRepository.findPricesBetween(symbols, from, to);
    }

    void save(Map<String, BigDecimal> prices, Instant updatedAt) {
        prices.forEach((symbol, price) -> {
            BigDecimal scaled = scaled(price);
            repository.upsert(UUID.randomUUID(), symbol, scaled, updatedAt);
            historyRepository.insert(symbol, List.of(new PricePoint(updatedAt, scaled)));
        });
    }

    void saveHistory(String symbol, List<PricePoint> points) {
        historyRepository.insert(symbol, points.stream()
                .map(point -> new PricePoint(point.time(), scaled(point.price())))
                .toList());
    }

    Set<String> symbolsWithHistoryUntil(Collection<String> symbols, Instant at) {
        return symbols.isEmpty() ? Set.of() : historyRepository.findSymbolsWithHistoryUntil(symbols, at);
    }

    private static BigDecimal scaled(BigDecimal price) {
        return price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
