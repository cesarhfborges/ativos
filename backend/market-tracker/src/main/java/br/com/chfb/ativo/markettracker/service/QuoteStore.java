package br.com.chfb.ativo.markettracker.service;

import br.com.chfb.ativo.markettracker.repository.MarketQuoteRepository;
import br.com.chfb.ativo.sharedkernel.market.Quote;
import br.com.chfb.ativo.sharedkernel.market.QuoteLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class QuoteStore implements QuoteLookup {

    private static final int PRICE_SCALE = 8;

    private final MarketQuoteRepository repository;

    public QuoteStore(MarketQuoteRepository repository) {
        this.repository = repository;
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

    void save(Map<String, BigDecimal> prices, Instant updatedAt) {
        prices.forEach((symbol, price) -> repository.upsert(
                UUID.randomUUID(), symbol, price.setScale(PRICE_SCALE, RoundingMode.HALF_UP), updatedAt));
    }
}
