package br.com.chfb.ativo.markettracker.service;

import br.com.chfb.ativo.markettracker.provider.CryptoMarketProvider;
import br.com.chfb.ativo.markettracker.provider.StockMarketProvider;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbols;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbolsProvider;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.settings.AppSettings;
import br.com.chfb.ativo.sharedkernel.settings.SettingKeys;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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
    /**
     * Quem não tem preço mais antigo que isso recebe a carga inicial. Se ela falhar, é refeita nas próximas coletas,
     * até a própria coleta alcançar essa idade; um símbolo que o provedor nunca cota continua sendo tentado.
     */
    private static final Duration BACKFILL_AGE = Duration.ofDays(1);

    private final ActiveSymbolsProvider activeSymbolsProvider;
    private final Optional<CryptoMarketProvider> cryptoProvider;
    private final Optional<StockMarketProvider> stockProvider;
    private final QuoteStore quoteStore;
    private final AppSettings appSettings;

    private volatile Instant lastCollectedAt = Instant.EPOCH;

    public QuoteCollector(
            ActiveSymbolsProvider activeSymbolsProvider,
            Optional<CryptoMarketProvider> cryptoProvider,
            Optional<StockMarketProvider> stockProvider,
            QuoteStore quoteStore,
            AppSettings appSettings) {
        this.appSettings = appSettings;
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
        backfill("cripto", active.crypto(), cryptoProvider.map(p -> p::fetchCryptoDailyCloses));
        backfill("ações", active.stocks(), stockProvider.map(p -> p::fetchStockDailyCloses));
        return lastCollectedAt;
    }

    /**
     * Limita as atualizações pedidas pelos usuários, que consomem a cota das APIs externas: devolve
     * {@code false} sem chamar os provedores se a última coleta foi há menos que o intervalo configurado.
     */
    public synchronized boolean refresh() {
        Duration interval = Duration.ofSeconds(appSettings.current().getInt(SettingKeys.MANUAL_REFRESH_SECONDS));
        if (Instant.now().isBefore(lastCollectedAt.plus(interval))) {
            return false;
        }
        collect();
        return true;
    }

    /** Última coleta desde que a aplicação iniciou; {@code null} se ainda não houve. */
    public Instant lastCollectedAt() {
        return Instant.EPOCH.equals(lastCollectedAt) ? null : lastCollectedAt;
    }

    private void backfill(String group, Set<String> symbols, Optional<Function<String, List<PricePoint>>> fetcher) {
        if (fetcher.isEmpty()) {
            return;
        }
        Set<String> covered = quoteStore.symbolsWithHistoryUntil(symbols, lastCollectedAt.minus(BACKFILL_AGE));
        for (String symbol : symbols) {
            if (covered.contains(symbol)) {
                continue;
            }
            try {
                List<PricePoint> closes = fetcher.get().apply(symbol);
                quoteStore.saveHistory(symbol, closes);
                log.info("Histórico de {} ({}): {} fechamentos diários", symbol, group, closes.size());
            } catch (RuntimeException e) {
                log.warn("Falha ao buscar o histórico de {} ({}): {}", symbol, group, e.getMessage());
            }
        }
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
