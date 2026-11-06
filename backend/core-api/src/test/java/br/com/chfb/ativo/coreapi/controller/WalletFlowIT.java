package br.com.chfb.ativo.coreapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
// Provedores desligados: os símbolos aleatórios dos testes não devem consultar a internet.
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {"market.provider.crypto=", "market.provider.stock="})
class WalletFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void compraEVendeAtualizandoPosicaoEHistorico() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();

        ResponseEntity<String> firstBuy = user.buy(symbol, "2", "100", "1.50");
        assertThat(firstBuy.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(read(firstBuy).at("/totalAmount").decimalValue()).isEqualByComparingTo("201.50");

        // Preço médio, com a taxa no custo: (2 x 100 + 1,50 + 2 x 200) / 4 = 150,375
        user.buy(symbol, "2", "200", "0");

        // 1 x 300 - 0,50 = 299,50
        ResponseEntity<String> sale = user.sell(symbol, "1", "300", "0.50");
        assertThat(sale.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(read(sale).at("/totalAmount").decimalValue()).isEqualByComparingTo("299.50");
        // 299,50 - (1 x 150,375) = 149,125
        assertThat(read(sale).at("/realizedProfit").decimalValue()).isEqualByComparingTo("149.125");
        assertThat(read(user.get("/api/dashboard/overview")).at("/realizedProfit").decimalValue())
                .isEqualByComparingTo("149.125");

        JsonNode positions = read(user.get("/api/dashboard/positions"));
        assertThat(positions.at("/0/symbol").asText()).isEqualTo(symbol);
        assertThat(positions.at("/0/quantity").decimalValue()).isEqualByComparingTo("3");
        assertThat(positions.at("/0/averagePrice").decimalValue()).isEqualByComparingTo("150.375");

        JsonNode statement = read(user.get("/api/transactions")).get("data");
        assertThat(statement).hasSize(3);
        assertThat(statement.get(0).get("type").asText()).isEqualTo("SELL");
        assertThat(statement.get(2).get("type").asText()).isEqualTo("BUY");
    }

    @Test
    void historicoFiltraPorTipo() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();
        user.buy(symbol, "2", "10", "0");
        user.sell(symbol, "1", "10", "0");

        JsonNode sales = read(user.get("/api/transactions?type=SELL")).get("data");

        assertThat(sales).hasSize(1);
        assertThat(sales.get(0).get("type").asText()).isEqualTo("SELL");
        assertThat(user.get("/api/transactions?type=DEPOSIT").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void extratoSegueOFormatoDoSimplePaginate() throws Exception {
        LoggedUser user = new LoggedUser();
        for (int i = 0; i < 5; i++) {
            user.buy(uniqueSymbol(), "1", "10", "0");
        }

        JsonNode first = read(user.get("/api/transactions?type=BUY&per_page=2"));
        assertThat(first.at("/current_page").asInt()).isEqualTo(1);
        assertThat(first.at("/data")).hasSize(2);
        assertThat(first.at("/per_page").asInt()).isEqualTo(2);
        assertThat(first.at("/from").asInt()).isEqualTo(1);
        assertThat(first.at("/to").asInt()).isEqualTo(2);
        assertThat(first.at("/path").asText()).isEqualTo("/api/transactions");
        assertThat(first.at("/first_page_url").asText()).isEqualTo("/api/transactions?type=BUY&per_page=2&page=1");
        assertThat(first.at("/next_page_url").asText()).isEqualTo("/api/transactions?type=BUY&per_page=2&page=2");
        assertThat(first.at("/prev_page_url").isNull()).isTrue();
        assertThat(first.has("total")).isFalse();
        assertThat(first.has("last_page")).isFalse();

        JsonNode last = read(user.get("/api/transactions?type=BUY&per_page=2&page=3"));
        assertThat(last.at("/data")).hasSize(1);
        assertThat(last.at("/from").asInt()).isEqualTo(5);
        assertThat(last.at("/to").asInt()).isEqualTo(5);
        assertThat(last.at("/next_page_url").isNull()).isTrue();
        assertThat(last.at("/prev_page_url").asText()).isEqualTo("/api/transactions?type=BUY&per_page=2&page=2");

        JsonNode beyond = read(user.get("/api/transactions?per_page=2&page=9"));
        assertThat(beyond.at("/data")).isEmpty();
        assertThat(beyond.at("/from").isNull()).isTrue();
        assertThat(beyond.at("/to").isNull()).isTrue();

        assertThat(user.get("/api/transactions?page=0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void paginaExataNaoAnunciaUmaProximaVazia() throws Exception {
        LoggedUser user = new LoggedUser();
        user.buy(uniqueSymbol(), "1", "10", "0");
        user.buy(uniqueSymbol(), "1", "10", "0");

        assertThat(read(user.get("/api/transactions?per_page=2")).at("/next_page_url").isNull()).isTrue();
    }

    @Test
    void extratoFiltraPorAtivoTipoDeAtivoDataValorESituacao() throws Exception {
        LoggedUser user = new LoggedUser();
        String prefix = uniqueSymbol();
        String stock = prefix + "A";
        String crypto = uniqueSymbol();
        user.post("/api/transactions/buy", """
                {"symbol":"%s","name":"Banco_Exemplo 100%%","assetType":"STOCK","quantity":1,"unitPrice":100,"fee":0,\
                "acceptUnverified":true,"executedAt":"2026-01-15T12:00:00Z"}""".formatted(stock));
        user.post("/api/transactions/buy", """
                {"symbol":"%s","name":"Moeda","assetType":"CRYPTO","quantity":1,"unitPrice":500,"fee":0,\
                "acceptUnverified":true,"executedAt":"2026-03-15T12:00:00Z"}""".formatted(crypto));
        String wrong = read(user.post("/api/transactions/buy", """
                {"symbol":"%s","assetType":"STOCK","quantity":1,"unitPrice":300,"fee":0}""".formatted(stock)))
                .at("/id")
                .asText();
        user.post("/api/transactions/" + wrong + "/reverse", null);

        assertThat(symbols(user, "asset=" + prefix.toLowerCase())).containsExactly(stock, stock);
        assertThat(symbols(user, "asset=banco_exemplo")).containsExactly(stock, stock);
        assertThat(symbols(user, "asset=Banco_Exemplo 100%")).containsExactly(stock, stock);
        assertThat(symbols(user, "asset=Banco%Exemplo")).isEmpty();
        assertThat(read(user.get("/api/transactions?asset=Banco_Exemplo 100%&per_page=1")).at("/next_page_url").asText())
                .isEqualTo("/api/transactions?asset=Banco_Exemplo%20100%25&per_page=1&page=2");
        assertThat(symbols(user, "assetType=CRYPTO")).containsExactly(crypto);
        assertThat(symbols(user, "assetType=STOCK&status=ACTIVE")).containsExactly(stock);
        assertThat(symbols(user, "status=REVERSED")).containsExactly(stock);
        assertThat(symbols(user, "from=2026-03-01T00:00:00Z&to=2026-03-15T12:00:00Z")).containsExactly(crypto);
        assertThat(symbols(user, "to=2026-01-15T11:59:59Z")).isEmpty();
        assertThat(symbols(user, "minTotal=300&maxTotal=500")).containsExactly(stock, crypto);
        assertThat(symbols(user, "minTotal=100&maxTotal=100")).containsExactly(stock);
        assertThat(symbols(user, "type=SELL")).isEmpty();
        assertThat(symbols(user, "assetType=CRYPTO&minTotal=501")).isEmpty();
    }

    @Test
    void filtrosDoExtratoInvalidosRetornam400() {
        LoggedUser user = new LoggedUser();

        for (String query : List.of(
                "assetType=BOND",
                "status=OUTRO",
                "from=ontem",
                "minTotal=-1",
                "minTotal=10&maxTotal=5",
                "from=2026-02-01T00:00:00Z&to=2026-01-01T00:00:00Z")) {
            assertThat(user.get("/api/transactions?" + query).getStatusCode()).as(query).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private static List<String> symbols(LoggedUser user, String query) throws Exception {
        List<String> symbols = new ArrayList<>();
        read(user.get("/api/transactions?" + query)).get("data").forEach(item -> symbols.add(item.get("symbol").asText()));
        return symbols;
    }

    @Test
    void painelSemCotacaoAvaliaPeloPrecoMedioSemLucro() throws Exception {
        LoggedUser user = new LoggedUser();
        user.buy(uniqueSymbol(), "2", "100", "0");

        JsonNode overview = read(user.get("/api/dashboard/overview"));

        assertThat(overview.at("/investedValue").decimalValue()).isEqualByComparingTo("200");
        assertThat(overview.at("/marketValue").decimalValue()).isEqualByComparingTo("200");
        assertThat(overview.at("/profitLoss").decimalValue()).isEqualByComparingTo("0");
        assertThat(overview.at("/positionsWithoutQuote").asInt()).isEqualTo(1);
    }

    @Test
    void painelUsaACotacaoParaCalcularLucroEPercentual() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();
        user.buy(symbol, "2", "100", "0");
        jdbcTemplate.update(
                "INSERT INTO market_quotes (id, symbol, price, last_updated_at) VALUES (?, ?, 125, now())",
                UUID.randomUUID(),
                symbol);

        JsonNode overview = read(user.get("/api/dashboard/overview"));

        assertThat(overview.at("/marketValue").decimalValue()).isEqualByComparingTo("250");
        assertThat(overview.at("/profitLoss").decimalValue()).isEqualByComparingTo("50");
        assertThat(overview.at("/profitLossPercent").decimalValue()).isEqualByComparingTo("25");
        assertThat(overview.at("/positionsWithoutQuote").asInt()).isZero();
    }

    @Test
    void mercadoMostraVariacaoEFaixaDosAtivosEmCarteira() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();
        user.buy(symbol, "1", "100", "0");
        jdbcTemplate.update(
                "INSERT INTO market_quotes (id, symbol, price, last_updated_at) VALUES (?, ?, 110, now())",
                UUID.randomUUID(),
                symbol);
        String history = "INSERT INTO market_quote_history (symbol, quoted_at, price) VALUES (?, now() - ?::interval, ?)";
        jdbcTemplate.update(history, symbol, "40 days", 500);
        jdbcTemplate.update(history, symbol, "20 days", 100);
        jdbcTemplate.update(history, symbol, "10 days", 80);
        jdbcTemplate.update(history, symbol, "2 days", 88);

        JsonNode market = read(user.get("/api/dashboard/market?days=30"));

        assertThat(market).hasSize(1);
        assertThat(market.at("/0/symbol").asText()).isEqualTo(symbol);
        assertThat(market.at("/0/periodChangePercent").decimalValue()).isEqualByComparingTo("10");
        assertThat(market.at("/0/change24hPercent").decimalValue()).isEqualByComparingTo("25");
        assertThat(market.at("/0/periodLow").decimalValue()).isEqualByComparingTo("80");
        assertThat(market.at("/0/periodHigh").decimalValue()).isEqualByComparingTo("110");
        assertThat(market.at("/0/prices")).hasSize(4);
    }

    @Test
    void estornoDeCompraSaiDosPontosEmQueElaValiaEPreservaOsDemais() throws Exception {
        LoggedUser user = new LoggedUser();
        String kept = uniqueSymbol();
        String wrong = uniqueSymbol();
        user.buy(kept, "1", "100", "0");
        Instant before = snapshot(user, "100", "123");

        String wrongBuy = read(user.buy(wrong, "2", "50", "0")).at("/id").asText();
        Instant during = Instant.now();
        price(kept, during, "110");
        price(wrong, during, "60");
        snapshot(user, during, "200", "230");

        user.post("/api/transactions/" + wrongBuy + "/reverse", null);
        Instant after = snapshot(user, "100", "777");

        JsonNode history = read(user.get("/api/dashboard/history"));

        assertThat(history).hasSize(4);
        assertPoint(history.get(0), before, "100", "123");
        assertPoint(history.get(1), during, "100", "110");
        assertPoint(history.get(2), after, "100", "777");
        assertBalances(history.get(3), "100", "100");
    }

    @Test
    void historicoTerminaNoValorAtualMesmoComOperacaoDepoisDoUltimoPonto() throws Exception {
        LoggedUser user = new LoggedUser();
        user.buy(uniqueSymbol(), "1", "100", "0");
        Instant recorded = snapshot(user, "100", "100");
        user.buy(uniqueSymbol(), "2", "50", "0");

        JsonNode history = read(user.get("/api/dashboard/history"));
        JsonNode overview = read(user.get("/api/dashboard/overview"));

        assertThat(history).hasSize(2);
        assertPoint(history.get(0), recorded, "100", "100");
        assertBalances(history.get(1), "200", "200");
        assertThat(Instant.parse(history.get(1).at("/time").asText())).isAfter(recorded);
        assertThat(overview.at("/investedValue").decimalValue()).isEqualByComparingTo("200");
        assertThat(overview.at("/marketValue").decimalValue()).isEqualByComparingTo("200");
    }

    @Test
    void estornoDeTodasAsOperacoesRemoveOsPontosDoHistorico() throws Exception {
        LoggedUser user = new LoggedUser();
        String buy = read(user.buy(uniqueSymbol(), "1", "100", "0")).at("/id").asText();
        snapshot(user, "100", "100");

        user.post("/api/transactions/" + buy + "/reverse", null);

        assertThat(read(user.get("/api/dashboard/history"))).isEmpty();
    }

    @Test
    void estornoDeVendaDevolveAPosicaoAosPontosEmQueElaValia() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();
        user.buy(symbol, "2", "100", "0");
        String sell = read(user.sell(symbol, "1", "150", "0")).at("/id").asText();
        Instant time = Instant.now();
        price(symbol, time, "130");
        snapshot(user, time, "100", "130");

        user.post("/api/transactions/" + sell + "/reverse", null);

        JsonNode history = read(user.get("/api/dashboard/history"));

        assertThat(history).hasSize(2);
        assertPoint(history.get(0), time, "200", "260");
        assertBalances(history.get(1), "200", "200");
    }

    @Test
    void estornoSemPrecoNoInstanteAvaliaOPontoPeloCusto() throws Exception {
        LoggedUser user = new LoggedUser();
        user.buy(uniqueSymbol(), "1", "80", "2");
        String wrong = read(user.buy(uniqueSymbol(), "1", "50", "0")).at("/id").asText();
        Instant time = snapshot(user, "132", "132");

        user.post("/api/transactions/" + wrong + "/reverse", null);

        JsonNode history = read(user.get("/api/dashboard/history"));

        assertThat(history).hasSize(2);
        assertPoint(history.get(0), time, "82", "82");
        assertBalances(history.get(1), "82", "82");
    }

    private Instant snapshot(LoggedUser user, String invested, String total) {
        Instant time = Instant.now();
        snapshot(user, time, invested, total);
        return time;
    }

    private void snapshot(LoggedUser user, Instant time, String invested, String total) {
        jdbcTemplate.update("""
                INSERT INTO portfolio_snapshots (id, wallet_id, invested_balance, total_balance, snapshot_time)
                SELECT ?, w.id, ?, ?, ? FROM wallets w JOIN users u ON u.id = w.user_id WHERE u.email = ?
                """,
                UUID.randomUUID(),
                new BigDecimal(invested),
                new BigDecimal(total),
                Timestamp.from(time),
                user.email);
    }

    private void price(String symbol, Instant time, String price) {
        jdbcTemplate.update(
                "INSERT INTO market_quote_history (symbol, quoted_at, price) VALUES (?, ?, ?)",
                symbol,
                Timestamp.from(time),
                new BigDecimal(price));
    }

    private static void assertPoint(JsonNode point, Instant time, String invested, String total) {
        assertThat(Instant.parse(point.at("/time").asText())).isCloseTo(time, within(1, ChronoUnit.MILLIS));
        assertBalances(point, invested, total);
    }

    private static void assertBalances(JsonNode point, String invested, String total) {
        assertThat(point.at("/investedBalance").decimalValue()).isEqualByComparingTo(invested);
        assertThat(point.at("/totalBalance").decimalValue()).isEqualByComparingTo(total);
    }

    @Test
    void contaSemOperacoesTemPainelEHistoricoVaziosSemCriarCarteira() throws Exception {
        LoggedUser user = new LoggedUser();

        assertThat(read(user.get("/api/dashboard/positions"))).isEmpty();
        assertThat(read(user.get("/api/transactions")).get("data")).isEmpty();
        assertThat(walletsOf(user.email)).isZero();
    }

    @Test
    void vendaSemCustodiaSuficienteERecusada() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();

        assertThat(user.sell(symbol, "1", "10", "0").getStatusCode())
                .as("ativo inexistente")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        user.buy(symbol, "1", "10", "0");

        assertThat(user.sell(symbol, "1.00000001", "10", "0").getStatusCode())
                .as("acima da custódia")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(read(user.get("/api/dashboard/positions")).at("/0/quantity").decimalValue())
                .isEqualByComparingTo("1");
    }

    @Test
    void vendaTotalTiraOAtivoDasPosicoes() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();
        user.buy(symbol, "1", "10", "0");

        assertThat(user.sell(symbol, "1", "10", "0").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(read(user.get("/api/dashboard/positions"))).isEmpty();
    }

    @Test
    void taxaMaiorQueAVendaERecusada() {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();
        user.buy(symbol, "1", "10", "0");

        assertThat(user.sell(symbol, "1", "10", "10.01").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void valoresInvalidosRetornam400() {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();

        assertThat(user.buy(symbol, "0", "10", "0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.buy(symbol, "1", "-1", "0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.buy(symbol, "1", "1.000000001", "0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.buy(symbol, "1", "1e1000000", "0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.get("/api/transactions?per_page=201").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.get("/api/dashboard/history?days=366").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void valorAcimaDaColunaRetorna422ENao500() {
        LoggedUser user = new LoggedUser();

        assertThat(user.buy(uniqueSymbol(), "9999999999999999", "99999999999999", "0").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void simboloEUnicoIgnorandoMaiusculasERecusaOutroTipo() {
        LoggedUser first = new LoggedUser();
        LoggedUser second = new LoggedUser();
        String symbol = uniqueSymbol();
        first.buy(symbol, "1", "10", "0");

        assertThat(second.buy(symbol.toLowerCase(), "1", "10", "0").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(assetsWithSymbol(symbol)).isEqualTo(1);

        ResponseEntity<String> otherType = second.post("/api/transactions/buy", """
                {"symbol":"%s","assetType":"STOCK","quantity":1,"unitPrice":10}""".formatted(symbol));
        assertThat(otherType.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void usuarioNaoVeNemVendeOQueEDeOutro() throws Exception {
        LoggedUser owner = new LoggedUser();
        LoggedUser other = new LoggedUser();
        String symbol = uniqueSymbol();
        owner.buy(symbol, "1", "10", "0");

        assertThat(read(other.get("/api/dashboard/positions"))).isEmpty();
        assertThat(read(other.get("/api/transactions")).get("data")).isEmpty();
        assertThat(other.sell(symbol, "1", "10", "0").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(read(owner.get("/api/dashboard/positions")).at("/0/quantity").decimalValue())
                .isEqualByComparingTo("1");
    }

    @Test
    void vendasSimultaneasNaoVendemAMesmaCustodiaDuasVezes() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();
        user.buy(symbol, "1", "10", "0");

        List<HttpStatus> statuses = runConcurrently(8, () -> user.sell(symbol, "0.6", "10", "0"));

        assertThat(statuses).containsOnlyOnce(HttpStatus.CREATED);
        assertThat(statuses).containsOnly(HttpStatus.CREATED, HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(read(user.get("/api/dashboard/positions")).at("/0/quantity").decimalValue())
                .isEqualByComparingTo("0.4");
    }

    @Test
    void primeirasComprasSimultaneasCriamUmaUnicaCarteira() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();

        List<HttpStatus> statuses = runConcurrently(8, () -> user.buy(symbol, "1", "10", "0"));

        assertThat(statuses).containsOnly(HttpStatus.CREATED);
        assertThat(walletsOf(user.email)).isEqualTo(1);
        assertThat(read(user.get("/api/dashboard/positions")).at("/0/quantity").decimalValue())
                .isEqualByComparingTo("8");
    }

    @Test
    void cadastroSimultaneoDoMesmoAtivoCriaUmUnicoRegistro() throws Exception {
        String symbol = uniqueSymbol();
        List<LoggedUser> users = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            users.add(new LoggedUser());
        }

        try (AutoClosePool pool = new AutoClosePool(4)) {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            users.forEach(user -> futures.add(pool.executor.submit(() -> user.buy(symbol, "1", "10", "0"))));
            for (Future<ResponseEntity<String>> future : futures) {
                assertThat(future.get().getStatusCode()).isEqualTo(HttpStatus.CREATED);
            }
        }

        assertThat(assetsWithSymbol(symbol)).isEqualTo(1);
    }

    @Test
    void semSessaoRetorna401() {
        assertThat(restTemplate.getForEntity("/api/dashboard/overview", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity("/api/transactions", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private List<HttpStatus> runConcurrently(int times, Callable<ResponseEntity<String>> call) throws Exception {
        try (AutoClosePool pool = new AutoClosePool(times)) {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int i = 0; i < times; i++) {
                futures.add(pool.executor.submit(call));
            }
            List<HttpStatus> statuses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                statuses.add(HttpStatus.valueOf(future.get().getStatusCode().value()));
            }
            return statuses;
        }
    }

    private int walletsOf(String email) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM wallets w JOIN users u ON u.id = w.user_id WHERE u.email = ?
                """, Integer.class, email);
    }

    private int assetsWithSymbol(String symbol) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM assets WHERE symbol = ?", Integer.class, symbol);
    }

    private static JsonNode read(ResponseEntity<String> response) throws Exception {
        return JSON.readTree(response.getBody());
    }

    private static String uniqueSymbol() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    /** O Java 17 ainda não tem {@code ExecutorService.close()}. */
    private static final class AutoClosePool implements AutoCloseable {

        final ExecutorService executor;

        AutoClosePool(int threads) {
            executor = Executors.newFixedThreadPool(threads);
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }

    /** Conta nova já logada, que guarda cookies e envia o token CSRF como o HttpClient do Angular. */
    private final class LoggedUser {

        final String email = "ana-" + UUID.randomUUID() + "@example.com";
        private final Map<String, String> cookies = new HashMap<>();

        LoggedUser() {
            get("/api/auth/csrf");
            String password = "senha-forte-123";
            post("/api/auth/register", """
                    {"name":"Ana","email":"%s","password":"%s","confirmPassword":"%s"}"""
                    .formatted(email, password, password));
            assertThat(post("/api/auth/login", """
                    {"email":"%s","password":"%s"}""".formatted(email, password)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<String> buy(String symbol, String quantity, String price, String fee) {
            return post("/api/transactions/buy", """
                    {"symbol":"%s","name":"Ativo de teste","assetType":"CRYPTO","quantity":%s,"unitPrice":%s,\
                    "fee":%s}""".formatted(symbol, quantity, price, fee));
        }

        ResponseEntity<String> sell(String symbol, String quantity, String price, String fee) {
            return post("/api/transactions/sell", """
                    {"symbol":"%s","quantity":%s,"unitPrice":%s,"fee":%s}""".formatted(symbol, quantity, price, fee));
        }

        ResponseEntity<String> get(String path) {
            return exchange(path, HttpMethod.GET, null);
        }

        ResponseEntity<String> post(String path, String body) {
            return exchange(path, HttpMethod.POST, body);
        }

        private synchronized HttpHeaders headers() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            cookies.forEach((name, value) -> headers.add(HttpHeaders.COOKIE, name + "=" + value));
            if (cookies.containsKey("XSRF-TOKEN")) {
                headers.add("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
            }
            return headers;
        }

        private ResponseEntity<String> exchange(String path, HttpMethod method, String body) {
            ResponseEntity<String> response =
                    restTemplate.exchange(path, method, new HttpEntity<>(body, headers()), String.class);
            response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()).forEach(this::store);
            return response;
        }

        private synchronized void store(String setCookie) {
            String[] parts = setCookie.split(";", 2)[0].split("=", 2);
            if (parts[1].isEmpty()) {
                cookies.remove(parts[0]);
            } else {
                cookies.put(parts[0], parts[1]);
            }
        }
    }
}
