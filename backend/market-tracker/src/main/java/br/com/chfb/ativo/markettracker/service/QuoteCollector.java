package br.com.chfb.ativo.markettracker.service;

import br.com.chfb.ativo.markettracker.entity.SyncItemStatus;
import br.com.chfb.ativo.markettracker.entity.SyncOrigin;
import br.com.chfb.ativo.markettracker.provider.CryptoMarketProvider;
import br.com.chfb.ativo.markettracker.provider.PriceFetch;
import br.com.chfb.ativo.markettracker.provider.StockMarketProvider;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbols;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbolsProvider;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.Quote;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import br.com.chfb.ativo.sharedkernel.settings.AppSettings;
import br.com.chfb.ativo.sharedkernel.settings.SettingKeys;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Coleta centralizada: cada símbolo é consultado uma vez, não importa quantos usuários o tenham, e o preço
 * vale para todos. Quem falha continua com a última cotação conhecida.
 */
@Service
public class QuoteCollector {

    private static final Logger log = LoggerFactory.getLogger(QuoteCollector.class);
    private static final String NO_PROVIDER = "Nenhum provedor configurado";
    private static final String NO_HISTORY = "O provedor não devolveu histórico";

    private final ActiveSymbolsProvider activeSymbolsProvider;
    private final Optional<CryptoMarketProvider> cryptoProvider;
    private final Optional<StockMarketProvider> stockProvider;
    private final QuoteStore quoteStore;
    private final AppSettings appSettings;
    private final SyncLog syncLog;

    public QuoteCollector(
            ActiveSymbolsProvider activeSymbolsProvider,
            Optional<CryptoMarketProvider> cryptoProvider,
            Optional<StockMarketProvider> stockProvider,
            QuoteStore quoteStore,
            AppSettings appSettings,
            SyncLog syncLog) {
        this.appSettings = appSettings;
        this.activeSymbolsProvider = activeSymbolsProvider;
        this.cryptoProvider = cryptoProvider;
        this.stockProvider = stockProvider;
        this.quoteStore = quoteStore;
        this.syncLog = syncLog;
    }

    public synchronized Instant collect(SyncOrigin origin) {
        SyncRunRecord run = syncLog.start(origin);
        try {
            ActiveSymbols active = activeSymbolsProvider.activeSymbols();
            Set<String> all = new HashSet<>(active.crypto());
            all.addAll(active.stocks());
            Instant collectedAt = collect(run, active.crypto(), active.stocks(), active.crypto(), active.stocks(), all);
            log.info("Coleta de cotações ({}): {} de {} símbolos atualizados", origin, run.updatedSymbols(), all.size());
            syncLog.finish(run);
            return collectedAt;
        } catch (RuntimeException e) {
            syncLog.fail(run, e);
            throw e;
        }
    }

    /**
     * Busca só o que falta de um símbolo que acabou de entrar em uma carteira. Se a coleta geral já o cobre,
     * não chama nenhum provedor. Pausada a coleta, não busca: o símbolo entra na próxima coleta geral.
     */
    public synchronized void collectNewSymbol(String symbol, AssetClass assetClass) {
        if (!appSettings.current().getBoolean(SettingKeys.COLLECTION_ENABLED)) {
            return;
        }
        boolean needsQuote = needsQuote(symbol);
        boolean needsHistory = quoteStore.symbolsWithContinuousHistory(Set.of(symbol), Instant.now()).isEmpty();
        if (!needsQuote && !needsHistory) {
            return;
        }
        SyncRunRecord run = syncLog.start(SyncOrigin.NEW_SYMBOL);
        try {
            Set<String> quote = needsQuote ? Set.of(symbol) : Set.of();
            Set<String> history = needsHistory ? Set.of(symbol) : Set.of();
            boolean crypto = assetClass == AssetClass.CRYPTO;
            collect(run, crypto ? quote : Set.of(), crypto ? Set.of() : quote, crypto ? history : Set.of(), crypto ? Set.of() : history, Set.of());
            syncLog.finish(run);
        } catch (RuntimeException e) {
            syncLog.fail(run, e);
            log.warn("Falha ao buscar o símbolo novo {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Pedida pelo admin, respeitando o intervalo mínimo: devolve {@code false} sem chamar os provedores se a
     * última coleta geral começou há menos que o intervalo configurado.
     */
    public synchronized boolean refresh() {
        Duration interval = Duration.ofSeconds(appSettings.current().getInt(SettingKeys.MANUAL_REFRESH_SECONDS));
        Instant last = syncLog.lastGeneralRunStartedAt();
        if (last != null && Instant.now().isBefore(last.plus(interval))) {
            return false;
        }
        collect(SyncOrigin.ADMIN);
        return true;
    }

    /** Última coleta geral que atualizou preços; {@code null} se nunca houve. */
    public Instant lastCollectedAt() {
        return syncLog.lastSyncAt();
    }

    private boolean needsQuote(String symbol) {
        Quote quote = quoteStore.findBySymbols(Set.of(symbol)).get(symbol);
        if (quote == null) {
            return true;
        }
        // Mais antiga que a última coleta geral: o símbolo não fazia parte dela.
        Instant lastRun = syncLog.lastGeneralRunStartedAt();
        return lastRun != null && quote.lastUpdatedAt().isBefore(lastRun);
    }

    /**
     * A cobertura do histórico é medida antes de gravar os preços: depois, todo símbolo pareceria recente,
     * e um que voltou a ser coletado ficaria com um buraco nos dados.
     */
    private Instant collect(
            SyncRunRecord run,
            Set<String> cryptoQuotes,
            Set<String> stockQuotes,
            Set<String> cryptoHistory,
            Set<String> stockHistory,
            Set<String> checkCoverage) {
        Set<String> covered = quoteStore.symbolsWithContinuousHistory(checkCoverage, Instant.now());

        Map<String, BigDecimal> prices = new HashMap<>();
        prices.putAll(fetch(run, AssetClass.CRYPTO, cryptoQuotes, cryptoProvider.map(p -> p::fetchCryptoPrices)));
        prices.putAll(fetch(run, AssetClass.STOCK, stockQuotes, stockProvider.map(p -> p::fetchStockPrices)));

        Instant collectedAt = Instant.now();
        quoteStore.save(prices, collectedAt);
        backfill(run, AssetClass.CRYPTO, cryptoHistory, covered, cryptoProvider.map(p -> p::fetchCryptoDailyCloses));
        backfill(run, AssetClass.STOCK, stockHistory, covered, stockProvider.map(p -> p::fetchStockDailyCloses));
        return collectedAt;
    }

    private void backfill(
            SyncRunRecord run,
            AssetClass assetClass,
            Set<String> symbols,
            Set<String> covered,
            Optional<Function<String, List<PricePoint>>> fetcher) {
        if (fetcher.isEmpty()) {
            return;
        }
        for (String symbol : symbols) {
            if (covered.contains(symbol)) {
                continue;
            }
            try {
                List<PricePoint> closes = fetcher.get().apply(symbol);
                quoteStore.saveHistory(symbol, closes);
                log.info("Histórico de {} ({}): {} fechamentos diários", symbol, assetClass, closes.size());
                if (closes.isEmpty()) {
                    run.history(symbol, assetClass, SyncItemStatus.NO_DATA, 0, NO_HISTORY);
                } else {
                    run.history(symbol, assetClass, SyncItemStatus.UPDATED, closes.size(), null);
                }
            } catch (RuntimeException e) {
                log.warn("Falha ao buscar o histórico de {} ({}): {}", symbol, assetClass, e.getMessage());
                run.history(symbol, assetClass, SyncItemStatus.FAILED, null, e.getMessage());
            }
        }
    }

    private Map<String, BigDecimal> fetch(
            SyncRunRecord run,
            AssetClass assetClass,
            Set<String> symbols,
            Optional<Function<Set<String>, PriceFetch>> fetcher) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        if (fetcher.isEmpty()) {
            log.warn("Nenhum provedor de {} configurado: {} símbolos sem atualização", assetClass, symbols.size());
            symbols.forEach(symbol -> run.quote(symbol, assetClass, SyncItemStatus.NO_DATA, NO_PROVIDER));
            return Map.of();
        }
        try {
            PriceFetch result = fetcher.get().apply(symbols);
            for (String symbol : symbols) {
                if (result.prices().containsKey(symbol)) {
                    run.quote(symbol, assetClass, SyncItemStatus.UPDATED, null);
                } else {
                    run.quote(symbol, assetClass, SyncItemStatus.FAILED, result.failures().get(symbol));
                }
            }
            return result.prices();
        } catch (RuntimeException e) {
            log.warn("Falha no provedor de {}; mantendo as últimas cotações: {}", assetClass, e.getMessage());
            symbols.forEach(symbol -> run.quote(symbol, assetClass, SyncItemStatus.FAILED, e.getMessage()));
            return Map.of();
        }
    }
}
