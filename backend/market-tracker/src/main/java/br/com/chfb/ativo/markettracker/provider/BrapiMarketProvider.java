package br.com.chfb.ativo.markettracker.provider;

import br.com.chfb.ativo.sharedkernel.market.AssetCandidate;
import br.com.chfb.ativo.sharedkernel.market.AssetSearchResult;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.SymbolCheck;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Uma chamada por símbolo, que é o limite do plano gratuito. O token vai no header para não aparecer
 * em logs de URL; sem token, a Brapi só responde para alguns ativos de teste.
 */
@Component
@ConditionalOnProperty(name = "market.provider.stock", havingValue = "brapi")
public class BrapiMarketProvider implements StockMarketProvider {

    private static final Logger log = LoggerFactory.getLogger(BrapiMarketProvider.class);
    /** Do maior para o menor: o plano gratuito não libera todos os períodos para todos os ativos. */
    private static final List<String> HISTORY_RANGES = List.of("1y", "3mo");
    /**
     * A Brapi data o candle pela meia-noite do pregão, e o fechamento só existe às 18h. Sem isso, o preço de hoje
     * pareceria conhecido desde a madrugada; datado no fim do dia, a variação de 24h à noite pularia um pregão.
     */
    private static final Duration MIDNIGHT_TO_CLOSE = Duration.ofHours(18);

    private static final String NO_PRICE = "A Brapi não devolveu preço";

    private final RestClient restClient;
    private final String baseUrl;
    private final String token;

    public BrapiMarketProvider(
            RestClient marketRestClient,
            @Value("${market.brapi.base-url}") String baseUrl,
            @Value("${market.brapi.token:}") String token) {
        this.restClient = marketRestClient;
        this.baseUrl = baseUrl;
        this.token = token;
    }

    @Override
    public List<PricePoint> fetchStockDailyCloses(String symbol) {
        for (String range : HISTORY_RANGES) {
            try {
                return dailyCloses(symbol, range);
            } catch (HttpClientErrorException e) {
                log.warn("Brapi sem histórico de {} para {}: {}", range, symbol, e.getMessage());
            }
        }
        return List.of();
    }

    private List<PricePoint> dailyCloses(String symbol, String range) {
        HistoryResponse response = restClient.get()
                .uri(baseUrl + "/api/quote/{symbol}?range={range}&interval=1d", symbol, range)
                .headers(headers -> {
                    if (!token.isBlank()) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    }
                })
                .retrieve()
                .body(HistoryResponse.class);
        if (response == null || response.results() == null || response.results().isEmpty()
                || response.results().get(0).historicalDataPrice() == null) {
            return List.of();
        }
        Instant now = Instant.now();
        return response.results().get(0).historicalDataPrice().stream()
                .filter(candle -> candle.date() != null && candle.close() != null)
                .map(candle -> new PricePoint(Instant.ofEpochSecond(candle.date()).plus(MIDNIGHT_TO_CLOSE), candle.close()))
                .filter(close -> close.time().isBefore(now))
                .toList();
    }

    @Override
    public PriceFetch fetchStockPrices(Set<String> symbols) {
        Map<String, BigDecimal> prices = new HashMap<>();
        Map<String, String> failures = new HashMap<>();
        for (String symbol : symbols) {
            try {
                QuoteResponse response = restClient.get()
                        .uri(baseUrl + "/api/quote/{symbol}", symbol)
                        .headers(headers -> {
                            if (!token.isBlank()) {
                                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                            }
                        })
                        .retrieve()
                        .body(QuoteResponse.class);
                BigDecimal price = response == null || response.results() == null || response.results().isEmpty()
                        ? null
                        : response.results().get(0).regularMarketPrice();
                if (price != null) {
                    prices.put(symbol, price);
                } else {
                    failures.put(symbol, NO_PRICE);
                }
            } catch (RestClientException e) {
                log.warn("Brapi sem cotação para {}: {}", symbol, e.getMessage());
                failures.put(symbol, e.getMessage());
            }
        }
        return new PriceFetch(prices, failures);
    }

    /** Sem token, a Brapi responde 401 até para ações reais: só o 404 diz que o símbolo não existe. */
    @Override
    public SymbolCheck checkStockSymbol(String symbol) {
        try {
            QuoteResponse response = restClient.get()
                    .uri(baseUrl + "/api/quote/{symbol}", symbol)
                    .headers(headers -> {
                        if (!token.isBlank()) {
                            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                        }
                    })
                    .retrieve()
                    .body(QuoteResponse.class);
            return response != null && response.results() != null && !response.results().isEmpty()
                    ? SymbolCheck.FOUND
                    : SymbolCheck.NOT_FOUND;
        } catch (HttpClientErrorException.NotFound e) {
            return SymbolCheck.NOT_FOUND;
        } catch (RestClientException e) {
            log.warn("Brapi não conferiu {}: {}", symbol, e.getMessage());
            return SymbolCheck.UNAVAILABLE;
        }
    }

    /** A listagem da Brapi responde sem token e já traz nome e último preço, sem uma chamada por ativo. */
    @Override
    public AssetSearchResult searchStocks(String query, int limit) {
        try {
            ListResponse response = restClient.get()
                    .uri(baseUrl + "/api/quote/list?search={query}&limit={limit}", query, limit)
                    .headers(headers -> {
                        if (!token.isBlank()) {
                            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                        }
                    })
                    .retrieve()
                    .body(ListResponse.class);
            if (response == null || response.stocks() == null) {
                return AssetSearchResult.found(List.of());
            }
            return AssetSearchResult.found(response.stocks().stream()
                    .filter(listed -> listed.stock() != null)
                    .map(listed -> new AssetCandidate(
                            listed.stock(),
                            listed.name() == null || listed.name().isBlank() ? listed.stock() : listed.name(),
                            AssetClass.STOCK,
                            listed.close()))
                    .toList());
        } catch (RestClientException e) {
            log.warn("Brapi não buscou ativos: {}", e.getMessage());
            return AssetSearchResult.unavailable();
        }
    }

    private record ListResponse(List<ListedStock> stocks) {
    }

    private record ListedStock(String stock, String name, BigDecimal close) {
    }

    private record HistoryResponse(List<HistoryResult> results) {
    }

    private record HistoryResult(List<Candle> historicalDataPrice) {
    }

    private record Candle(Long date, BigDecimal close) {
    }

    private record QuoteResponse(List<Result> results) {
    }

    private record Result(BigDecimal regularMarketPrice) {
    }
}
