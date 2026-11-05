package br.com.chfb.ativo.markettracker.provider;

import java.math.BigDecimal;
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
    public Map<String, BigDecimal> fetchStockPrices(Set<String> symbols) {
        Map<String, BigDecimal> prices = new HashMap<>();
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
                if (response != null && response.results() != null && !response.results().isEmpty()) {
                    BigDecimal price = response.results().get(0).regularMarketPrice();
                    if (price != null) {
                        prices.put(symbol, price);
                    }
                }
            } catch (RestClientException e) {
                log.warn("Brapi sem cotação para {}: {}", symbol, e.getMessage());
            }
        }
        return prices;
    }

    private record QuoteResponse(List<Result> results) {
    }

    private record Result(BigDecimal regularMarketPrice) {
    }
}
