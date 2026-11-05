package br.com.chfb.ativo.coreapi.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
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

        // Preço médio: (2 x 100 + 2 x 200) / 4 = 150
        user.buy(symbol, "2", "200", "0");

        // 1 x 300 - 0,50 = 299,50
        ResponseEntity<String> sale = user.sell(symbol, "1", "300", "0.50");
        assertThat(sale.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(read(sale).at("/totalAmount").decimalValue()).isEqualByComparingTo("299.50");

        JsonNode positions = read(user.get("/api/dashboard/positions"));
        assertThat(positions.at("/0/symbol").asText()).isEqualTo(symbol);
        assertThat(positions.at("/0/quantity").decimalValue()).isEqualByComparingTo("3");
        assertThat(positions.at("/0/averagePrice").decimalValue()).isEqualByComparingTo("150");

        JsonNode statement = read(user.get("/api/transactions"));
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

        JsonNode sales = read(user.get("/api/transactions?type=SELL"));

        assertThat(sales).hasSize(1);
        assertThat(sales.get(0).get("type").asText()).isEqualTo("SELL");
        assertThat(user.get("/api/transactions?type=DEPOSIT").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
    void contaSemOperacoesTemPainelEHistoricoVaziosSemCriarCarteira() throws Exception {
        LoggedUser user = new LoggedUser();

        assertThat(read(user.get("/api/dashboard/positions"))).isEmpty();
        assertThat(read(user.get("/api/transactions"))).isEmpty();
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
        assertThat(user.buy(symbol, "1", "1.00001", "0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.buy(symbol, "1", "1e1000000", "0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.get("/api/transactions?size=201").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
        assertThat(read(other.get("/api/transactions"))).isEmpty();
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
                    {"name":"Ana","email":"%s","password":"%s"}""".formatted(email, password));
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
