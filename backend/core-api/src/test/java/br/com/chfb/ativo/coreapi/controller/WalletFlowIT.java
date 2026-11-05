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
    void depositaCompraVendeEAtualizaSaldoEPosicao() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();

        assertThat(user.deposit("1000").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 1000 - (2 x 100 + 1,50) = 798,50
        ResponseEntity<String> firstBuy = user.buy(symbol, "2", "100", "1.50", false);
        assertThat(firstBuy.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(read(firstBuy).at("/transaction/totalAmount").decimalValue()).isEqualByComparingTo("201.50");
        assertThat(read(firstBuy).at("/fiatBalance").decimalValue()).isEqualByComparingTo("798.50");

        // Preço médio: (2 x 100 + 2 x 200) / 4 = 150. Caixa: 798,50 - 400 = 398,50
        user.buy(symbol, "2", "200", "0", false);

        // 398,50 + (1 x 300 - 0,50) = 698
        ResponseEntity<String> sale = user.sell(symbol, "1", "300", "0.50");
        assertThat(sale.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(read(sale).at("/transaction/totalAmount").decimalValue()).isEqualByComparingTo("299.50");

        JsonNode summary = read(user.get("/api/wallets/summary"));
        assertThat(summary.at("/fiatBalance").decimalValue()).isEqualByComparingTo("698");
        assertThat(summary.at("/positions/0/symbol").asText()).isEqualTo(symbol);
        assertThat(summary.at("/positions/0/quantity").decimalValue()).isEqualByComparingTo("3");
        assertThat(summary.at("/positions/0/averagePrice").decimalValue()).isEqualByComparingTo("150");

        JsonNode statement = read(user.get("/api/transactions"));
        assertThat(statement).hasSize(4);
        assertThat(statement.get(0).get("type").asText()).isEqualTo("SELL");
        assertThat(statement.get(3).get("type").asText()).isEqualTo("DEPOSIT");
    }

    @Test
    void contaSemMovimentacaoTemResumoEExtratoVaziosSemCriarCarteira() throws Exception {
        LoggedUser user = new LoggedUser();

        JsonNode summary = read(user.get("/api/wallets/summary"));

        assertThat(summary.at("/fiatBalance").decimalValue()).isEqualByComparingTo("0");
        assertThat(summary.at("/positions")).isEmpty();
        assertThat(read(user.get("/api/transactions"))).isEmpty();
        assertThat(walletsOf(user.email)).isZero();
    }

    @Test
    void compraComSaldoInsuficienteERecusadaSemDeixarRastro() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();
        user.deposit("100");

        ResponseEntity<String> buy = user.buy(symbol, "1", "100", "0.01", false);

        assertThat(buy.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode summary = read(user.get("/api/wallets/summary"));
        assertThat(summary.at("/fiatBalance").decimalValue()).isEqualByComparingTo("100");
        assertThat(summary.at("/positions")).isEmpty();
        assertThat(read(user.get("/api/transactions"))).hasSize(1);
        assertThat(assetsWithSymbol(symbol)).isZero();
    }

    @Test
    void compraComSaldoExatoZeraOCaixa() throws Exception {
        LoggedUser user = new LoggedUser();
        user.deposit("100.01");

        ResponseEntity<String> buy = user.buy(uniqueSymbol(), "1", "100", "0.01", false);

        assertThat(buy.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(read(buy).at("/fiatBalance").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void autoAporteDepositaSoADiferencaERegistraOsDoisLancamentos() throws Exception {
        LoggedUser user = new LoggedUser();
        user.deposit("30");

        ResponseEntity<String> buy = user.buy(uniqueSymbol(), "1", "100", "0", true);

        assertThat(buy.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = read(buy);
        assertThat(body.at("/autoDeposit/totalAmount").decimalValue()).isEqualByComparingTo("70");
        assertThat(body.at("/fiatBalance").decimalValue()).isEqualByComparingTo("0");

        List<String> types = new ArrayList<>();
        read(user.get("/api/transactions")).forEach(entry -> types.add(entry.get("type").asText()));
        assertThat(types).containsExactlyInAnyOrder("DEPOSIT", "DEPOSIT", "BUY");
    }

    @Test
    void autoAporteNaoDepositaQuandoOSaldoBasta() throws Exception {
        LoggedUser user = new LoggedUser();
        user.deposit("100");

        JsonNode body = read(user.buy(uniqueSymbol(), "1", "100", "0", true));

        assertThat(body.at("/autoDeposit").isNull()).isTrue();
        assertThat(read(user.get("/api/transactions"))).hasSize(2);
    }

    @Test
    void vendaSemCustodiaSuficienteERecusada() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();

        assertThat(user.sell(symbol, "1", "10", "0").getStatusCode())
                .as("ativo inexistente")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        user.deposit("100");
        user.buy(symbol, "1", "10", "0", false);

        assertThat(user.sell(symbol, "1.00000001", "10", "0").getStatusCode())
                .as("acima da custódia")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        JsonNode summary = read(user.get("/api/wallets/summary"));
        assertThat(summary.at("/fiatBalance").decimalValue()).isEqualByComparingTo("90");
        assertThat(summary.at("/positions/0/quantity").decimalValue()).isEqualByComparingTo("1");
    }

    @Test
    void vendaTotalTiraOAtivoDoResumo() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();
        user.deposit("100");
        user.buy(symbol, "1", "10", "0", false);

        assertThat(user.sell(symbol, "1", "10", "0").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(read(user.get("/api/wallets/summary")).at("/positions")).isEmpty();
    }

    @Test
    void taxaMaiorQueAVendaERecusada() throws Exception {
        LoggedUser user = new LoggedUser();
        String symbol = uniqueSymbol();
        user.deposit("100");
        user.buy(symbol, "1", "10", "0", false);

        assertThat(user.sell(symbol, "1", "10", "10.01").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void resgateAcimaDoSaldoERecusado() throws Exception {
        LoggedUser user = new LoggedUser();
        user.deposit("100");

        assertThat(user.fiat("WITHDRAW", "100.01").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(user.fiat("WITHDRAW", "100").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(read(user.get("/api/wallets/summary")).at("/fiatBalance").decimalValue())
                .isEqualByComparingTo("0");
    }

    @Test
    void valoresInvalidosRetornam400() {
        LoggedUser user = new LoggedUser();

        assertThat(user.fiat("DEPOSIT", "0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.fiat("DEPOSIT", "-1").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.fiat("DEPOSIT", "1.00001").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.fiat("DEPOSIT", "1e1000000").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.fiat("BUY", "1").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(user.get("/api/transactions?size=201").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void valorAcimaDaColunaRetorna422ENao500() {
        LoggedUser user = new LoggedUser();
        user.deposit("99999999999999");

        assertThat(user.deposit("1").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(user.buy(uniqueSymbol(), "9999999999999999", "99999999999999", "0", true).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void simboloEUnicoIgnorandoMaiusculasERecusaOutroTipo() throws Exception {
        LoggedUser first = new LoggedUser();
        LoggedUser second = new LoggedUser();
        String symbol = uniqueSymbol();
        first.deposit("100");
        second.deposit("100");
        first.buy(symbol, "1", "10", "0", false);

        assertThat(second.buy(symbol.toLowerCase(), "1", "10", "0", false).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(assetsWithSymbol(symbol)).isEqualTo(1);

        ResponseEntity<String> otherType = second.post("/api/transactions/buy", """
                {"symbol":"%s","assetType":"STOCK","quantity":1,"unitPrice":10}""".formatted(symbol));
        assertThat(otherType.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(read(second.get("/api/wallets/summary")).at("/fiatBalance").decimalValue())
                .isEqualByComparingTo("90");
    }

    @Test
    void usuarioNaoVeNemVendeOQueEDeOutro() throws Exception {
        LoggedUser owner = new LoggedUser();
        LoggedUser other = new LoggedUser();
        String symbol = uniqueSymbol();
        owner.deposit("100");
        owner.buy(symbol, "1", "10", "0", false);

        assertThat(read(other.get("/api/wallets/summary")).at("/positions")).isEmpty();
        assertThat(read(other.get("/api/transactions"))).isEmpty();
        assertThat(other.sell(symbol, "1", "10", "0").getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(read(owner.get("/api/wallets/summary")).at("/positions/0/quantity").decimalValue())
                .isEqualByComparingTo("1");
    }

    @Test
    void operacoesSimultaneasNaoGastamOMesmoSaldoDuasVezes() throws Exception {
        LoggedUser user = new LoggedUser();
        user.deposit("100");

        List<HttpStatus> statuses = runConcurrently(8, () -> user.fiat("WITHDRAW", "60"));

        assertThat(statuses).containsOnlyOnce(HttpStatus.CREATED);
        assertThat(statuses).containsOnly(HttpStatus.CREATED, HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(read(user.get("/api/wallets/summary")).at("/fiatBalance").decimalValue())
                .isEqualByComparingTo("40");
    }

    @Test
    void primeiroDepositoSimultaneoCriaUmaUnicaCarteira() throws Exception {
        LoggedUser user = new LoggedUser();

        List<HttpStatus> statuses = runConcurrently(8, () -> user.deposit("10"));

        assertThat(statuses).containsOnly(HttpStatus.CREATED);
        assertThat(walletsOf(user.email)).isEqualTo(1);
        assertThat(read(user.get("/api/wallets/summary")).at("/fiatBalance").decimalValue())
                .isEqualByComparingTo("80");
    }

    @Test
    void cadastroSimultaneoDoMesmoAtivoCriaUmUnicoRegistro() throws Exception {
        String symbol = uniqueSymbol();
        List<LoggedUser> users = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LoggedUser user = new LoggedUser();
            user.deposit("100");
            users.add(user);
        }

        try (AutoClosePool pool = new AutoClosePool(4)) {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            users.forEach(user -> futures.add(pool.executor.submit(() -> user.buy(symbol, "1", "10", "0", false))));
            for (Future<ResponseEntity<String>> future : futures) {
                assertThat(future.get().getStatusCode()).isEqualTo(HttpStatus.CREATED);
            }
        }

        assertThat(assetsWithSymbol(symbol)).isEqualTo(1);
    }

    @Test
    void semSessaoRetorna401() {
        assertThat(restTemplate.getForEntity("/api/wallets/summary", String.class).getStatusCode())
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

        ResponseEntity<String> deposit(String amount) {
            return fiat("DEPOSIT", amount);
        }

        ResponseEntity<String> fiat(String type, String amount) {
            return post("/api/wallets/fiat-movement", """
                    {"type":"%s","amount":%s}""".formatted(type, amount));
        }

        ResponseEntity<String> buy(String symbol, String quantity, String price, String fee, boolean autoDeposit) {
            return post("/api/transactions/buy", """
                    {"symbol":"%s","name":"Ativo de teste","assetType":"CRYPTO","quantity":%s,"unitPrice":%s,\
                    "fee":%s,"autoDeposit":%s}""".formatted(symbol, quantity, price, fee, autoDeposit));
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
