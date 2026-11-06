package br.com.chfb.ativo.markettracker.provider;

import br.com.chfb.ativo.sharedkernel.market.AssetCandidate;
import br.com.chfb.ativo.sharedkernel.market.AssetSearchResult;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.SymbolCheck;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Uma chamada por símbolo: na API da Binance, um par inexistente em lote derrubaria o lote inteiro. */
@Component
@ConditionalOnProperty(name = "market.provider.crypto", havingValue = "binance")
public class BinanceMarketProvider implements CryptoMarketProvider {

    private static final Logger log = LoggerFactory.getLogger(BinanceMarketProvider.class);
    private static final int DAILY_CLOSES = 365;
    /** Posições do preço de fechamento e do fim do período em cada vela da Binance, que vem como lista. */
    private static final int CANDLE_CLOSE = 4;
    private static final int CANDLE_CLOSE_TIME = 6;
    private static final String INVALID_SYMBOL_CODE = "-1121";
    private static final int NO_MATCH = Integer.MAX_VALUE;
    /** A lista de pares pesa alguns megabytes e muda raramente; a busca a consulta a cada digitação. */
    private static final Duration TRADABLE_ASSETS_TTL = Duration.ofHours(1);
    /** A API oficial não informa o nome dos ativos: sem entrada aqui, o nome é o próprio símbolo. */
    private static final Map<String, String> ASSET_NAMES = Map.ofEntries(
            Map.entry("ADA", "Cardano"),
            Map.entry("AVAX", "Avalanche"),
            Map.entry("BNB", "BNB"),
            Map.entry("BTC", "Bitcoin"),
            Map.entry("DOGE", "Dogecoin"),
            Map.entry("ETH", "Ethereum"),
            Map.entry("LINK", "Chainlink"),
            Map.entry("LTC", "Litecoin"),
            Map.entry("NEAR", "NEAR Protocol"),
            Map.entry("PEPE", "Pepe"),
            Map.entry("POL", "Polygon"),
            Map.entry("RENDER", "Render"),
            Map.entry("SHIB", "Shiba Inu"),
            Map.entry("SOL", "Solana"),
            Map.entry("SUI", "Sui"),
            Map.entry("USDC", "USD Coin"),
            Map.entry("USDT", "Tether"),
            Map.entry("XRP", "XRP"));

    private final RestClient restClient;
    private final String baseUrl;
    private final String quoteAsset;
    private volatile TradableAssets tradableAssets;

    public BinanceMarketProvider(
            RestClient marketRestClient,
            @Value("${market.binance.base-url}") String baseUrl,
            @Value("${market.binance.quote-asset}") String quoteAsset) {
        this.restClient = marketRestClient;
        this.baseUrl = baseUrl;
        this.quoteAsset = quoteAsset;
    }

    @Override
    public Map<String, BigDecimal> fetchCryptoPrices(Set<String> symbols) {
        Map<String, BigDecimal> prices = new HashMap<>();
        for (String symbol : symbols) {
            try {
                TickerPrice ticker = restClient.get()
                        .uri(baseUrl + "/api/v3/ticker/price?symbol={pair}", symbol + quoteAsset)
                        .retrieve()
                        .body(TickerPrice.class);
                if (ticker != null && ticker.price() != null) {
                    prices.put(symbol, ticker.price());
                }
            } catch (RestClientException e) {
                log.warn("Binance sem cotação para {}{}: {}", symbol, quoteAsset, e.getMessage());
            }
        }
        return prices;
    }

    @Override
    public List<PricePoint> fetchCryptoDailyCloses(String symbol) {
        List<List<Object>> candles;
        try {
            candles = restClient.get()
                    .uri(baseUrl + "/api/v3/klines?symbol={pair}&interval=1d&limit={limit}", symbol + quoteAsset, DAILY_CLOSES)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (HttpClientErrorException e) {
            log.warn("Binance sem histórico para {}{}: {}", symbol, quoteAsset, e.getMessage());
            return List.of();
        }
        if (candles == null) {
            return List.of();
        }
        Instant now = Instant.now();
        return candles.stream()
                .map(candle -> new PricePoint(
                        Instant.ofEpochMilli(((Number) candle.get(CANDLE_CLOSE_TIME)).longValue()),
                        new BigDecimal(candle.get(CANDLE_CLOSE).toString())))
                .filter(close -> close.time().isBefore(now))
                .toList();
    }

    /** A Binance responde 400 com o código -1121 para um par que não existe. */
    @Override
    public SymbolCheck checkCryptoSymbol(String symbol) {
        try {
            restClient.get()
                    .uri(baseUrl + "/api/v3/ticker/price?symbol={pair}", symbol + quoteAsset)
                    .retrieve()
                    .toBodilessEntity();
            return SymbolCheck.FOUND;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 400 && e.getResponseBodyAsString().contains(INVALID_SYMBOL_CODE)) {
                return SymbolCheck.NOT_FOUND;
            }
            log.warn("Binance não conferiu {}{}: {}", symbol, quoteAsset, e.getMessage());
            return SymbolCheck.UNAVAILABLE;
        } catch (RestClientException e) {
            log.warn("Binance não conferiu {}{}: {}", symbol, quoteAsset, e.getMessage());
            return SymbolCheck.UNAVAILABLE;
        }
    }

    /** Sem a cotação, o ativo ainda aparece: o usuário só precisa informar o preço. */
    @Override
    public AssetSearchResult searchCrypto(String query, int limit) {
        List<String> tradable = tradableAssets();
        if (tradable == null) {
            return AssetSearchResult.unavailable();
        }
        String term = query.toUpperCase(Locale.ROOT);
        List<String> matches = tradable.stream()
                .filter(asset -> relevance(asset, term) < NO_MATCH)
                .sorted(Comparator.comparingInt(asset -> relevance(asset, term)))
                .limit(limit)
                .toList();
        Map<String, BigDecimal> prices = pricesOf(matches);
        return AssetSearchResult.found(matches.stream()
                .map(asset -> new AssetCandidate(asset, nameOf(asset), AssetClass.CRYPTO, prices.get(asset)))
                .toList());
    }

    private static int relevance(String asset, String term) {
        if (asset.equals(term)) {
            return 0;
        }
        if (asset.startsWith(term)) {
            return 1;
        }
        return nameOf(asset).toUpperCase(Locale.ROOT).contains(term) ? 2 : NO_MATCH;
    }

    private static String nameOf(String asset) {
        return ASSET_NAMES.getOrDefault(asset, asset);
    }

    /** Se a Binance falhar, vale a última lista conhecida; nulo só quando nunca houve uma. */
    private List<String> tradableAssets() {
        TradableAssets cached = tradableAssets;
        if (cached != null && cached.fetchedAt().plus(TRADABLE_ASSETS_TTL).isAfter(Instant.now())) {
            return cached.assets();
        }
        try {
            ExchangeInfo info = restClient.get()
                    .uri(baseUrl + "/api/v3/exchangeInfo?permissions=SPOT&symbolStatus=TRADING&showPermissionSets=false")
                    .retrieve()
                    .body(ExchangeInfo.class);
            if (info == null || info.symbols() == null) {
                return cached == null ? null : cached.assets();
            }
            List<String> assets = info.symbols().stream()
                    .filter(pair -> quoteAsset.equals(pair.quoteAsset()) && "TRADING".equals(pair.status()))
                    .map(ExchangePair::baseAsset)
                    .toList();
            tradableAssets = new TradableAssets(assets, Instant.now());
            return assets;
        } catch (RestClientException e) {
            log.warn("Binance não listou os pares em {}: {}", quoteAsset, e.getMessage());
            return cached == null ? null : cached.assets();
        }
    }

    private Map<String, BigDecimal> pricesOf(List<String> assets) {
        if (assets.isEmpty()) {
            return Map.of();
        }
        String pairs = assets.stream().map(asset -> '"' + asset + quoteAsset + '"').collect(Collectors.joining(",", "[", "]"));
        try {
            List<TickerPrice> tickers = restClient.get()
                    .uri(baseUrl + "/api/v3/ticker/price?symbols={pairs}", pairs)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (tickers == null) {
                return Map.of();
            }
            return tickers.stream()
                    .filter(ticker -> ticker.symbol() != null && ticker.price() != null && ticker.symbol().endsWith(quoteAsset))
                    .collect(Collectors.toMap(
                            ticker -> ticker.symbol().substring(0, ticker.symbol().length() - quoteAsset.length()),
                            TickerPrice::price));
        } catch (RestClientException e) {
            log.warn("Binance sem cotação para a busca: {}", e.getMessage());
            return Map.of();
        }
    }

    private record TradableAssets(List<String> assets, Instant fetchedAt) {
    }

    private record ExchangeInfo(List<ExchangePair> symbols) {
    }

    private record ExchangePair(String status, String baseAsset, String quoteAsset) {
    }

    private record TickerPrice(String symbol, BigDecimal price) {
    }
}
