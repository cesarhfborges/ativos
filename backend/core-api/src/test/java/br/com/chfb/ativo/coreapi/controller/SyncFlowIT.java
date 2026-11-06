package br.com.chfb.ativo.coreapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.chfb.ativo.markettracker.entity.SyncOrigin;
import br.com.chfb.ativo.markettracker.provider.CryptoMarketProvider;
import br.com.chfb.ativo.markettracker.provider.PriceFetch;
import br.com.chfb.ativo.markettracker.provider.StockMarketProvider;
import br.com.chfb.ativo.markettracker.service.QuoteCollectionJob;
import br.com.chfb.ativo.markettracker.service.QuoteCollector;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.SymbolCheck;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"market.provider.crypto=", "market.provider.stock=", "market.collect-on-startup=false"})
class SyncFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private QuoteCollector collector;

    @Autowired
    private QuoteCollectionJob job;

    @MockitoBean
    private CryptoMarketProvider crypto;

    @MockitoBean
    private StockMarketProvider stock;

    @BeforeEach
    void provedorRespondeTudo() {
        when(crypto.checkCryptoSymbol(anyString())).thenReturn(SymbolCheck.FOUND);
        when(crypto.fetchCryptoPrices(anySet())).thenAnswer(call -> {
            Set<String> symbols = call.getArgument(0);
            return new PriceFetch(symbols.stream().collect(Collectors.toMap(s -> s, s -> BigDecimal.TEN)), Map.of());
        });
        when(crypto.fetchCryptoDailyCloses(anyString())).thenReturn(List.of(
                new PricePoint(Instant.now().minus(Duration.ofDays(30)), BigDecimal.ONE)));
    }

    @Test
    void doisUsuariosComOMesmoAtivoGeramUmaSoConsulta() throws Exception {
        String symbol = uniqueSymbol();

        assertThat(new Session(false).buy(symbol).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(crypto, timeout(5000).times(1)).fetchCryptoPrices(Set.of(symbol));
        await().atMost(Duration.ofSeconds(5)).until(() -> runsWith(symbol, "NEW_SYMBOL") == 1);

        // Já coberto pela primeira compra: a compra do segundo usuário não pode chamar o provedor.
        assertThat(new Session(false).buy(symbol).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(crypto, after(1000).times(1)).fetchCryptoPrices(Set.of(symbol));
        assertThat(runsWith(symbol, "NEW_SYMBOL")).isEqualTo(1);

        collector.collect(SyncOrigin.SCHEDULED);

        Integer items = jdbc.queryForObject(
                """
                SELECT count(*) FROM sync_run_items i JOIN sync_runs r ON r.id = i.run_id
                WHERE i.symbol = ? AND i.kind = 'QUOTE' AND r.origin = 'SCHEDULED'
                """,
                Integer.class,
                symbol);
        assertThat(items).isEqualTo(1);
    }

    @Test
    void ativoQueVoltouDepoisDeUmBuracoRecebeOHistoricoDeNovo() {
        String symbol = uniqueSymbol();
        jdbc.update(
                "INSERT INTO market_quote_history (symbol, quoted_at, price) VALUES (?, ?, 1)",
                symbol,
                Timestamp.from(Instant.now().minus(Duration.ofDays(100))));

        new Session(false).buy(symbol);

        verify(crypto, timeout(5000)).fetchCryptoDailyCloses(symbol);
    }

    @Test
    void usuarioComumNaoForcaColeta() {
        Session user = new Session(false);

        assertThat(user.post("/api/dashboard/refresh-quotes").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(user.post("/api/admin/quotes/collect").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(user.get("/api/admin/sync/runs").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void todosVeemAUltimaEAProximaSincronizacao() throws Exception {
        assertThat(restTemplate.getForEntity("/api/market/sync-status", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        collector.collect(SyncOrigin.SCHEDULED);

        JsonNode status = JSON.readTree(new Session(false).get("/api/market/sync-status").getBody());
        assertThat(Instant.parse(status.at("/lastSyncAt").asText())).isBeforeOrEqualTo(Instant.now());
        assertThat(Instant.parse(status.at("/nextSyncAt").asText())).isAfter(Instant.now());
        assertThat(status.at("/lastRunFailed").asBoolean()).isFalse();
        assertThat(status.at("/collectionEnabled").asBoolean()).isTrue();
    }

    @Test
    void buscaDeSimboloNovoNaoContaComoSincronizacaoGeral() throws Exception {
        String symbol = uniqueSymbol();
        jdbc.update("DELETE FROM sync_runs");
        new Session(false).buy(symbol);
        await().atMost(Duration.ofSeconds(5)).until(() -> runsWith(symbol, "NEW_SYMBOL") == 1);

        JsonNode status = JSON.readTree(new Session(false).get("/api/market/sync-status").getBody());

        assertThat(status.at("/lastSyncAt").isNull()).isTrue();
    }

    @Test
    void adminVeOsRegistrosComFiltrosEDetalhe() throws Exception {
        String symbol = uniqueSymbol();
        new Session(false).buy(symbol);
        verify(crypto, timeout(5000)).fetchCryptoPrices(Set.of(symbol));
        await().atMost(Duration.ofSeconds(5)).until(() -> runsWith(symbol, "NEW_SYMBOL") == 1);
        Session admin = new Session(true);

        JsonNode page = JSON.readTree(admin.get("/api/admin/sync/runs?symbol=" + symbol.toLowerCase(Locale.ROOT)).getBody());
        assertThat(page.at("/total").asLong()).isEqualTo(1);
        assertThat(page.at("/items/0/origin").asText()).isEqualTo("NEW_SYMBOL");
        assertThat(page.at("/items/0/status").asText()).isEqualTo("SUCCESS");
        assertThat(page.at("/items/0/updatedSymbols").asInt()).isEqualTo(1);

        JsonNode items = JSON.readTree(admin.get("/api/admin/sync/runs/" + page.at("/items/0/id").asText() + "/items").getBody());
        assertThat(items.at("/0/symbol").asText()).isEqualTo(symbol);

        JsonNode filtered = JSON.readTree(admin.get("/api/admin/sync/runs?origin=ADMIN&status=FAILED&symbol=" + symbol).getBody());
        assertThat(filtered.at("/total").asLong()).isZero();

        assertThat(admin.get("/api/admin/sync/runs?status=INVALIDO").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void coletaAgendadaApagaRegistrosForaDaRetencao() {
        UUID old = insertRun("SUCCESS", Instant.now().minus(Duration.ofDays(200)));
        UUID recent = insertRun("SUCCESS", Instant.now().minus(Duration.ofDays(10)));

        job.collectHourly();

        assertThat(statusOf(old)).isEmpty();
        assertThat(statusOf(recent)).containsExactly("SUCCESS");
    }

    @Test
    void execucaoPelaMetadeFicaInterrompidaAoIniciar() {
        UUID running = insertRun("RUNNING", Instant.now());

        job.collectOnStartup();

        assertThat(statusOf(running)).containsExactly("INTERRUPTED");
    }

    private UUID insertRun(String status, Instant startedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO sync_runs (id, origin, status, started_at) VALUES (?, 'SCHEDULED', ?, ?)",
                id,
                status,
                Timestamp.from(startedAt));
        return id;
    }

    private List<String> statusOf(UUID id) {
        return jdbc.queryForList("SELECT status FROM sync_runs WHERE id = ?", String.class, id);
    }

    /** Conta só execuções já concluídas, com o resultado do símbolo gravado. */
    private int runsWith(String symbol, String origin) {
        Integer total = jdbc.queryForObject(
                """
                SELECT count(DISTINCT r.id) FROM sync_runs r JOIN sync_run_items i ON i.run_id = r.id
                WHERE i.symbol = ? AND r.origin = ?
                """,
                Integer.class,
                symbol,
                origin);
        return total == null ? 0 : total;
    }

    private static String uniqueSymbol() {
        return "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private final class Session {

        private final Map<String, String> cookies = new HashMap<>();

        Session(boolean admin) {
            String email = "sync-" + UUID.randomUUID() + "@example.com";
            String password = "senha-forte-123";
            get("/api/auth/csrf");
            post("/api/auth/register", """
                    {"name":"Ana","email":"%s","password":"%s","confirmPassword":"%s"}"""
                    .formatted(email, password, password));
            if (admin) {
                jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
            }
            assertThat(post("/api/auth/login", """
                    {"email":"%s","password":"%s"}""".formatted(email, password)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<String> buy(String symbol) {
            return post("/api/transactions/buy", """
                    {"symbol":"%s","name":"Ativo de teste","assetType":"CRYPTO","quantity":1,"unitPrice":10,"fee":0}"""
                    .formatted(symbol));
        }

        ResponseEntity<String> get(String path) {
            return exchange(path, HttpMethod.GET, null);
        }

        ResponseEntity<String> post(String path) {
            return exchange(path, HttpMethod.POST, null);
        }

        ResponseEntity<String> post(String path, String body) {
            return exchange(path, HttpMethod.POST, body);
        }

        private ResponseEntity<String> exchange(String path, HttpMethod method, String body) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            cookies.forEach((name, value) -> headers.add(HttpHeaders.COOKIE, name + "=" + value));
            if (cookies.containsKey("XSRF-TOKEN")) {
                headers.add("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
            }
            ResponseEntity<String> response =
                    restTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
            response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()).forEach(cookie -> {
                String[] parts = cookie.split(";", 2)[0].split("=", 2);
                if (parts[1].isEmpty()) {
                    cookies.remove(parts[0]);
                } else {
                    cookies.put(parts[0], parts[1]);
                }
            });
            return response;
        }
    }
}
