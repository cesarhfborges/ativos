package br.com.chfb.ativo.markettracker.service;

import br.com.chfb.ativo.markettracker.provider.CryptoMarketProvider;
import br.com.chfb.ativo.markettracker.provider.StockMarketProvider;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbols;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbolsProvider;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Busca nos provedores só o que está em custódia. Quem falha continua com a última cotação conhecida. */
@Service
public class QuoteCollector {

    private static final Logger log = LoggerFactory.getLogger(QuoteCollector.class);
    /** Limita as atualizações pedidas pelos usuários, que consomem a cota das APIs externas. */
    private static final Duration MANUAL_REFRESH_INTERVAL = Duration.ofMinutes(1);

    private final ActiveSymbolsProvider activeSymbolsProvider;
    private final Optional<CryptoMarketProvider> cryptoProvider;
    private final Optional<StockMarketProvider> stockProvider;
    private final QuoteStore quoteStore;

    private Instant lastCollectedAt = Instant.EPOCH;

    public QuoteCollector(
            ActiveSymbolsProvider activeSymbolsProvider,
            Optional<CryptoMarketProvider> cryptoProvider,
            Optional<StockMarketProvider> stockProvider,
            QuoteStore quoteStore) {
        this.activeSymbolsProvider = activeSymbolsProvider;
        this.cryptoProvider = cryptoProvider;
        this.stockProvider = stockProvider;
        this.quoteStore = quoteStore;
    }

    public synchronized Instant collect() {
        ActiveSymbols active = activeSymbolsProvider.activeSymbols();
        Map<String, BigDecimal> prices = new HashMap<>();
        prices.putAll(fetch("cripto", active.crypto(), cryptoProvider.map(p -> p::fetchCryptoPrices)));
        prices.putAll(fetch("ações", active.stocks(), stockProvider.map(p -> p::fetchStockPrices)));

        lastCollectedAt = Instant.now();
        quoteStore.save(prices, lastCollectedAt);
        log.info(
                "Coleta de cotações: {} de {} símbolos atualizados",
                prices.size(),
                active.crypto().size() + active.stocks().size());
        return lastCollectedAt;
    }

    /** Devolve {@code false} sem chamar os provedores se a última coleta foi há menos de um minuto. */
    public synchronized boolean refresh() {
        if (Instant.now().isBefore(lastCollectedAt.plus(MANUAL_REFRESH_INTERVAL))) {
            return false;
        }
        collect();
        return true;
    }

    private Map<String, BigDecimal> fetch(
            String group,
            Set<String> symbols,
            Optional<Function<Set<String>, Map<String, BigDecimal>>> fetcher) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        if (fetcher.isEmpty()) {
            log.warn("Nenhum provedor de {} configurado: {} símbolos sem atualização", group, symbols.size());
            return Map.of();
        }
        try {
            return fetcher.get().apply(symbols);
        } catch (RuntimeException e) {
            log.warn("Falha no provedor de {}; mantendo as últimas cotações: {}", group, e.getMessage());
            return Map.of();
        }
    }
}
