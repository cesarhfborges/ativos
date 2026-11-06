package br.com.chfb.ativo.coreapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import br.com.chfb.ativo.markettracker.service.QuoteCollector;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbols;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbolsProvider;
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
import org.mockito.Mockito;
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
class WatchlistFlowIT {

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
    private ActiveSymbolsProvider activeSymbols;

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
    void acompanharNaoAlteraOPatrimonioNemACarteira() throws Exception {
        Session user = new Session();

        ResponseEntity<String> added = user.add(uniqueSymbol(), "\"buyBelow\":9,\"sellAbove\":20,\"stopLoss\":5,\"note\":\" Comprar na queda \"");

        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode item = JSON.readTree(added.getBody());
        assertThat(item.at("/buyBelow").decimalValue()).isEqualByComparingTo("9");
        assertThat(item.at("/note").asText()).isEqualTo("Comprar na queda");
        assertThat(item.at("/quantity").decimalValue()).isEqualByComparingTo("0");
        assertThat(item.at("/averagePrice").isNull()).isTrue();

        JsonNode overview = JSON.readTree(user.get("/api/dashboard/overview").getBody());
        assertThat(overview.at("/marketValue").decimalValue()).isEqualByComparingTo("0");
        assertThat(JSON.readTree(user.get("/api/dashboard/positions").getBody())).isEmpty();
    }

    @Test
    void ativoAcompanhadoEntraNaColetaUmaVezSo() throws Exception {
        String symbol = uniqueSymbol();
        Session first = new Session();
        Session second = new Session();

        first.add(symbol, "");
        verify(crypto, timeout(5000).times(1)).fetchCryptoPrices(Set.of(symbol));

        // Já coberto: o segundo usuário e a compra do primeiro não chamam o provedor de novo.
        second.add(symbol, "");
        first.buy(symbol);
        verify(crypto, after(1000).times(1)).fetchCryptoPrices(Set.of(symbol));

        ActiveSymbols active = activeSymbols.activeSymbols();
        assertThat(active.crypto()).contains(symbol);

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
    void ativoDeixaDeSerColetadoQuandoNinguemAcompanha() throws Exception {
        String symbol = uniqueSymbol();
        Session user = new Session();
        String id = JSON.readTree(user.add(symbol, "").getBody()).at("/id").asText();
        assertThat(activeSymbols.activeSymbols().crypto()).contains(symbol);

        assertThat(user.exchange("/api/watchlist/" + id, HttpMethod.DELETE, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(activeSymbols.activeSymbols().crypto()).doesNotContain(symbol);
        assertThat(JSON.readTree(user.get("/api/watchlist").getBody())).isEmpty();
    }

    @Test
    void mostraAPosicaoEOsDadosDeMercadoDoProprioUsuario() throws Exception {
        String symbol = uniqueSymbol();
        Session holder = new Session();
        Session watcher = new Session();
        holder.add(symbol, "");
        watcher.add(symbol, "");
        holder.buy(symbol);
        jdbc.update(
                "INSERT INTO market_quote_history (symbol, quoted_at, price) VALUES (?, ?, 5) ON CONFLICT DO NOTHING",
                symbol,
                Timestamp.from(Instant.now().minus(Duration.ofDays(3))));
        collector.collect(SyncOrigin.SCHEDULED);

        JsonNode held = JSON.readTree(holder.get("/api/watchlist?days=7").getBody()).get(0);
        assertThat(held.at("/quantity").decimalValue()).isEqualByComparingTo("1");
        assertThat(held.at("/averagePrice").decimalValue()).isEqualByComparingTo("10");
        assertThat(held.at("/currentPrice").decimalValue()).isEqualByComparingTo("10");
        assertThat(held.at("/prices")).isNotEmpty();

        JsonNode watched = JSON.readTree(watcher.get("/api/watchlist?days=7").getBody()).get(0);
        assertThat(watched.at("/quantity").decimalValue()).isEqualByComparingTo("0");
        assertThat(watched.at("/currentPrice").decimalValue()).isEqualByComparingTo("10");
    }

    @Test
    void sinalTecnicoEIgualParaTodosESaiDoHistoricoSemChamarOProvedor() throws Exception {
        String symbol = uniqueSymbol();
        Instant now = Instant.now();
        for (int day = 1; day <= 60; day++) {
            jdbc.update(
                    "INSERT INTO market_quote_history (symbol, quoted_at, price) VALUES (?, ?, ?)",
                    symbol,
                    Timestamp.from(now.minus(Duration.ofDays(day))),
                    BigDecimal.valueOf(1000 - day));
        }
        withCurrentQuote(symbol, 1000);
        Session first = new Session();
        Session second = new Session();
        first.add(symbol, "");
        second.add(symbol, "");
        Mockito.clearInvocations(crypto);

        JsonNode signal = JSON.readTree(first.get("/api/watchlist?days=1").getBody()).at("/0/signal");
        JsonNode other = JSON.readTree(second.get("/api/watchlist?days=30").getBody()).at("/0/signal");

        assertThat(signal.at("/available").asBoolean()).isTrue();
        assertThat(signal.at("/rsi").decimalValue()).isEqualByComparingTo("100");
        assertThat(signal.at("/momentum").asText()).isEqualTo("OVERBOUGHT");
        assertThat(signal.at("/trend").asText()).isEqualTo("UP");
        assertThat(other).isEqualTo(signal);
        Mockito.verifyNoInteractions(crypto, stock);
    }

    @Test
    void osLimitesDoAdminValemNaHoraParaOSinal() throws Exception {
        String symbol = uniqueSymbol();
        Instant now = Instant.now();
        for (int day = 1; day <= 60; day++) {
            // Sobe e desce o mesmo tanto: o RSI fica perto do meio da escala.
            jdbc.update(
                    "INSERT INTO market_quote_history (symbol, quoted_at, price) VALUES (?, ?, ?)",
                    symbol,
                    Timestamp.from(now.minus(Duration.ofDays(day))),
                    BigDecimal.valueOf(day % 2 == 0 ? 100 : 101));
        }
        withCurrentQuote(symbol, 100);
        Session user = new Session();
        user.add(symbol, "");
        assertThat(JSON.readTree(user.get("/api/watchlist").getBody()).at("/0/signal/momentum").asText()).isEqualTo("NEUTRAL");

        jdbc.update(
                """
                INSERT INTO app_settings (key, value, updated_at, updated_by) VALUES ('predictive.signal.overbought', '40', now(), 'teste')
                ON CONFLICT (key) DO UPDATE SET value = '40'
                """);
        try {
            JsonNode signal = JSON.readTree(user.get("/api/watchlist").getBody()).at("/0/signal");
            assertThat(signal.at("/momentum").asText()).isEqualTo("OVERBOUGHT");
            assertThat(signal.at("/overboughtLimit").asInt()).isEqualTo(40);
        } finally {
            jdbc.update("DELETE FROM app_settings WHERE key = 'predictive.signal.overbought'");
        }
    }

    @Test
    void semHistoricoOSinalFicaIndisponivel() throws Exception {
        Session user = new Session();

        JsonNode signal = JSON.readTree(user.add(uniqueSymbol(), "").getBody()).at("/signal");

        assertThat(signal.at("/available").asBoolean()).isFalse();
        assertThat(signal.at("/requiredCloses").asInt()).isEqualTo(50);
    }

    @Test
    void buscaNoCatalogoTrazACotacaoGravadaSemChamarOProvedor() throws Exception {
        String quoted = uniqueSymbol();
        String withoutQuote = uniqueSymbol();
        Session user = new Session();
        user.add(quoted, "");
        user.add(withoutQuote, "");
        jdbc.update("DELETE FROM market_quotes WHERE symbol IN (?, ?)", quoted, withoutQuote);
        withCurrentQuote(quoted, 123);
        Mockito.clearInvocations(crypto, stock);

        JsonNode found = JSON.readTree(user.get("/api/assets?query=" + quoted).getBody()).get(0);
        JsonNode missing = JSON.readTree(user.get("/api/assets?query=" + withoutQuote).getBody()).get(0);

        assertThat(found.at("/price").decimalValue()).isEqualByComparingTo("123");
        assertThat(missing.at("/price").isNull()).isTrue();
        Mockito.verifyNoInteractions(crypto, stock);
    }

    @Test
    void naoAlcancaItemDeOutroUsuario() throws Exception {
        Session owner = new Session();
        Session other = new Session();
        String id = JSON.readTree(owner.add(uniqueSymbol(), "\"note\":\"privado\"").getBody()).at("/id").asText();

        assertThat(JSON.readTree(other.get("/api/watchlist").getBody())).isEmpty();
        assertThat(other.exchange("/api/watchlist/" + id, HttpMethod.PUT, "{\"note\":\"invadido\"}").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(other.exchange("/api/watchlist/" + id, HttpMethod.DELETE, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JSON.readTree(owner.get("/api/watchlist").getBody()).at("/0/note").asText()).isEqualTo("privado");
    }

    @Test
    void alteraOPlano() throws Exception {
        Session user = new Session();
        String id = JSON.readTree(user.add(uniqueSymbol(), "\"buyBelow\":9,\"note\":\"antes\"").getBody()).at("/id").asText();

        ResponseEntity<String> updated = user.exchange("/api/watchlist/" + id, HttpMethod.PUT, "{\"sellAbove\":30,\"note\":\" \"}");

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode item = JSON.readTree(updated.getBody());
        assertThat(item.at("/buyBelow").isNull()).isTrue();
        assertThat(item.at("/sellAbove").decimalValue()).isEqualByComparingTo("30");
        assertThat(item.at("/note").isNull()).isTrue();
    }

    @Test
    void recusaPlanoIncoerenteOuInvalido() {
        Session user = new Session();

        assertThat(user.add(uniqueSymbol(), "\"buyBelow\":30,\"sellAbove\":20").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(user.add(uniqueSymbol(), "\"stopLoss\":30,\"sellAbove\":20").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(user.add(uniqueSymbol(), "\"buyBelow\":-1").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.add(uniqueSymbol(), "\"note\":\"" + "x".repeat(501) + "\"").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void naoAcompanhaOMesmoAtivoDuasVezes() {
        Session user = new Session();
        String symbol = uniqueSymbol();
        user.add(symbol, "");

        assertThat(user.add(symbol.toLowerCase(Locale.ROOT), "").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void respeitaOLimiteConfigurado() {
        jdbc.update(
                """
                INSERT INTO app_settings (key, value, updated_at, updated_by) VALUES ('watchlist.max-items', '1', now(), 'teste')
                ON CONFLICT (key) DO UPDATE SET value = '1'
                """);
        try {
            Session user = new Session();
            assertThat(user.add(uniqueSymbol(), "").getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(user.add(uniqueSymbol(), "").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        } finally {
            jdbc.update("DELETE FROM app_settings WHERE key = 'watchlist.max-items'");
        }
    }

    @Test
    void simboloInexistenteRespondeComoNaCompra() throws Exception {
        when(crypto.checkCryptoSymbol("NAOEXISTE")).thenReturn(SymbolCheck.NOT_FOUND);
        when(stock.checkStockSymbol("NAOEXISTE")).thenReturn(SymbolCheck.NOT_FOUND);
        Session user = new Session();

        ResponseEntity<String> refused = user.add("NAOEXISTE", "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(JSON.readTree(refused.getBody()).at("/code").asText()).isEqualTo("SYMBOL_NOT_FOUND");
    }

    @Test
    void adminNaoRemoveDoCatalogoUmAtivoAcompanhado() throws Exception {
        String symbol = uniqueSymbol();
        new Session().add(symbol, "");
        UUID asset = jdbc.queryForObject("SELECT id FROM assets WHERE symbol = ?", UUID.class, symbol);
        Session admin = new Session();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", admin.email);
        admin.login();

        assertThat(admin.exchange("/api/admin/assets/" + asset, HttpMethod.DELETE, null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /** Já coberto pela coleta: adicionar o ativo não busca um preço novo no provedor simulado. */
    private void withCurrentQuote(String symbol, int price) {
        jdbc.update(
                "INSERT INTO market_quotes (id, symbol, price, last_updated_at) VALUES (?, ?, ?, now())",
                UUID.randomUUID(),
                symbol,
                BigDecimal.valueOf(price));
    }

    private static String uniqueSymbol() {
        return "W" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private final class Session {

        final String email = "watch-" + UUID.randomUUID() + "@example.com";
        private final Map<String, String> cookies = new HashMap<>();

        Session() {
            get("/api/auth/csrf");
            exchange("/api/auth/register", HttpMethod.POST, """
                    {"name":"Ana","email":"%s","password":"senha-forte-123","confirmPassword":"senha-forte-123"}""".formatted(email));
            login();
        }

        void login() {
            assertThat(exchange("/api/auth/login", HttpMethod.POST, """
                    {"email":"%s","password":"senha-forte-123"}""".formatted(email)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<String> add(String symbol, String plan) {
            String extra = plan.isEmpty() ? "" : "," + plan;
            return exchange("/api/watchlist", HttpMethod.POST, """
                    {"symbol":"%s","name":"Ativo de teste","assetType":"CRYPTO"%s}""".formatted(symbol, extra));
        }

        ResponseEntity<String> buy(String symbol) {
            return exchange("/api/transactions/buy", HttpMethod.POST, """
                    {"symbol":"%s","name":"Ativo de teste","assetType":"CRYPTO","quantity":1,"unitPrice":10,"fee":0}"""
                    .formatted(symbol));
        }

        ResponseEntity<String> get(String path) {
            return exchange(path, HttpMethod.GET, null);
        }

        ResponseEntity<String> exchange(String path, HttpMethod method, String body) {
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
