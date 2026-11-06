package br.com.chfb.ativo.coreapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import br.com.chfb.ativo.markettracker.provider.CryptoMarketProvider;
import br.com.chfb.ativo.markettracker.provider.PriceFetch;
import br.com.chfb.ativo.markettracker.provider.StockMarketProvider;
import br.com.chfb.ativo.markettracker.service.QuoteCollectionJob;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.SymbolCheck;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Da coleta agendada à leitura da carteira, com os preços gravados de verdade. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"market.provider.crypto=", "market.provider.stock=", "market.collect-on-startup=false"})
class SentimentFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int HISTORY_DAYS = 80;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private QuoteCollectionJob job;

    @MockitoBean
    private CryptoMarketProvider crypto;

    @MockitoBean
    private StockMarketProvider stock;

    private final Map<String, String> cookies = new HashMap<>();

    @BeforeEach
    void provedorRespondeTudo() {
        when(crypto.checkCryptoSymbol(anyString())).thenReturn(SymbolCheck.FOUND);
        when(crypto.fetchCryptoPrices(anySet())).thenAnswer(call -> {
            Set<String> symbols = call.getArgument(0);
            return new PriceFetch(symbols.stream().collect(Collectors.toMap(s -> s, s -> BigDecimal.valueOf(500))), Map.of());
        });
        // Alta constante: o índice precisa terminar do lado da ganância.
        Instant now = Instant.now();
        when(crypto.fetchCryptoDailyCloses(anyString())).thenReturn(IntStream.range(0, HISTORY_DAYS)
                .mapToObj(i -> new PricePoint(now.minus(Duration.ofDays(HISTORY_DAYS - i)), BigDecimal.valueOf(100 + i)))
                .toList());
    }

    @Test
    void aColetaAgendadaGravaAsNotasEOPainelCombinaComACarteira() throws Exception {
        String symbol = "S" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        login();
        assertThat(exchange("/api/transactions/buy", HttpMethod.POST, """
                {"symbol":"%s","name":"Ativo de teste","assetType":"CRYPTO","quantity":1,"unitPrice":10,"fee":0}"""
                .formatted(symbol)).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode before = JSON.readTree(exchange("/api/dashboard/sentiment?days=30", HttpMethod.GET, null).getBody());
        assertThat(before.get("current").isNull()).isTrue();

        job.collectHourly();

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM market_sentiment_daily WHERE symbol = ?", Integer.class, symbol);
        assertThat(rows).isPositive();
        Integer snapshots = jdbc.queryForObject("SELECT count(*) FROM portfolio_snapshots", Integer.class);
        assertThat(snapshots).isPositive();

        JsonNode sentiment = JSON.readTree(exchange("/api/dashboard/sentiment?days=30", HttpMethod.GET, null).getBody());
        assertThat(sentiment.at("/current/score").asDouble()).isGreaterThan(55);
        assertThat(sentiment.at("/current/coverage").asDouble()).isEqualTo(1);
        assertThat(sentiment.get("points")).isNotEmpty();
        assertThat(sentiment.get("stale").asBoolean()).isFalse();

        // Recalcular o último dia substitui a linha, em vez de duplicá-la.
        job.collectHourly();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM market_sentiment_daily WHERE symbol = ?", Integer.class, symbol))
                .isEqualTo(rows);
    }

    @Test
    void periodoInvalidoEDesconhecidoSaoRecusados() {
        assertThat(exchange("/api/dashboard/sentiment", HttpMethod.GET, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        login();
        assertThat(exchange("/api/dashboard/sentiment?days=0", HttpMethod.GET, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange("/api/dashboard/sentiment?days=366", HttpMethod.GET, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void login() {
        String email = "sent-" + UUID.randomUUID() + "@example.com";
        exchange("/api/auth/csrf", HttpMethod.GET, null);
        exchange("/api/auth/register", HttpMethod.POST, """
                {"name":"Ana","email":"%s","password":"senha-forte-123","confirmPassword":"senha-forte-123"}""".formatted(email));
        assertThat(exchange("/api/auth/login", HttpMethod.POST, """
                {"email":"%s","password":"senha-forte-123"}""".formatted(email)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        cookies.forEach((name, value) -> headers.add(HttpHeaders.COOKIE, name + "=" + value));
        if (cookies.containsKey("XSRF-TOKEN")) {
            headers.add("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
        }
        ResponseEntity<String> response = restTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
        response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()).forEach(cookie -> {
            String[] parts = cookie.split(";", 2)[0].split("=", 2);
            cookies.put(parts[0], parts[1]);
        });
        return response;
    }
}
