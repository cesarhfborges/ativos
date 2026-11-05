package br.com.chfb.ativo.markettracker.provider;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Uma chamada por símbolo: na API da Binance, um par inexistente em lote derrubaria o lote inteiro. */
@Component
@ConditionalOnProperty(name = "market.provider.crypto", havingValue = "binance")
public class BinanceMarketProvider implements CryptoMarketProvider {

    private static final Logger log = LoggerFactory.getLogger(BinanceMarketProvider.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final String quoteAsset;

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

    private record TickerPrice(String symbol, BigDecimal price) {
    }
}
