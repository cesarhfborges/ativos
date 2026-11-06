package br.com.chfb.ativo.coreapi.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** Percorre o fluxo real pela porta HTTP, com os cookies de um navegador simulados em {@link Browser}. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AuthFlowIT {

    private static final String PASSWORD = "senha-forte-123";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registroLoginSessaoELogout() {
        String email = uniqueEmail();
        Browser browser = new Browser();

        ResponseEntity<String> registered = browser.post("/api/auth/register", registerBody(email));
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registered.getBody()).contains(email).doesNotContain("password");

        assertThat(browser.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> login = browser.post("/api/auth/login", loginBody(email, PASSWORD));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).contains(email);

        String sessionCookie = setCookie(login, "JSESSIONID");
        assertThat(sessionCookie).contains("HttpOnly").containsIgnoringCase("SameSite=Lax");

        Integer sessions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE principal_name = ?", Integer.class, email);
        assertThat(sessions).isEqualTo(1);

        // Passa pela desserialização da sessão gravada no banco.
        ResponseEntity<String> me = browser.get("/api/auth/me");
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains(email);

        assertThat(browser.post("/api/auth/logout", "").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(browser.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE principal_name = ?", Integer.class, email)).isZero();
    }

    @Test
    void senhaGuardadaComoBcrypt() {
        String email = uniqueEmail();
        new Browser().post("/api/auth/register", registerBody(email));

        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);

        assertThat(hash).startsWith("$2").doesNotContain(PASSWORD);
    }

    @Test
    void loginTrocaOIdDaSessaoExistente() {
        String email = uniqueEmail();
        Browser browser = new Browser();
        browser.post("/api/auth/register", registerBody(email));
        browser.post("/api/auth/login", loginBody(email, PASSWORD));
        String firstSession = browser.cookies.get("JSESSIONID");

        browser.post("/api/auth/login", loginBody(email, PASSWORD));

        assertThat(browser.cookies.get("JSESSIONID")).isNotEqualTo(firstSession);
        assertThat(browser.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void emailDuplicadoIgnoraMaiusculasERetorna409() {
        String email = uniqueEmail();
        Browser browser = new Browser();
        browser.post("/api/auth/register", registerBody(email));

        ResponseEntity<String> duplicated = browser.post("/api/auth/register", registerBody(email.toUpperCase()));

        assertThat(duplicated.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void loginAceitaEmailComMaiusculas() {
        String email = uniqueEmail();
        Browser browser = new Browser();
        browser.post("/api/auth/register", registerBody(email));

        assertThat(browser.post("/api/auth/login", loginBody(email.toUpperCase(), PASSWORD)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void senhaErradaEEmailInexistenteRespondemIgual() {
        String email = uniqueEmail();
        Browser browser = new Browser();
        browser.post("/api/auth/register", registerBody(email));

        ResponseEntity<String> wrongPassword = browser.post("/api/auth/login", loginBody(email, "senha-errada-123"));
        ResponseEntity<String> unknownEmail = browser.post("/api/auth/login", loginBody(uniqueEmail(), PASSWORD));

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody()).isEqualTo(unknownEmail.getBody());
        assertThat(browser.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registroInvalidoRetorna400ComOsCampos() {
        ResponseEntity<String> tooShort = new Browser().post("/api/auth/register",
                "{\"name\":\"Ana\",\"email\":\"nao-e-email\",\"password\":\"curta\"}");

        assertThat(tooShort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooShort.getBody()).contains("\"field\":\"email\"", "\"field\":\"password\"");

        // 40 caracteres acentuados cabem no @Size, mas ocupam 80 bytes: acima do limite do BCrypt.
        ResponseEntity<String> mismatch = new Browser().post("/api/auth/register",
                "{\"name\":\"Ana\",\"email\":\"%s\",\"password\":\"senha-forte-123\",\"confirmPassword\":\"outra-senha-123\"}"
                        .formatted(uniqueEmail()));
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> tooManyBytes = new Browser().post("/api/auth/register",
                registerBody(uniqueEmail(), "ç".repeat(40)));

        assertThat(tooManyBytes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void postSemTokenCsrfRetorna403() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange("/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(registerBody(uniqueEmail()), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static String uniqueEmail() {
        return "ana-" + UUID.randomUUID() + "@example.com";
    }

    private static String registerBody(String email) {
        return registerBody(email, PASSWORD);
    }

    private static String registerBody(String email, String password) {
        return "{\"name\":\"Ana\",\"email\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}"
                .formatted(email, password, password);
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private static String setCookie(ResponseEntity<?> response, String name) {
        return response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()).stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie ausente: " + name));
    }

    /** Guarda cookies entre chamadas e envia o token CSRF, como faz o HttpClient do Angular. */
    private class Browser {

        final Map<String, String> cookies = new HashMap<>();

        Browser() {
            get("/api/auth/csrf");
        }

        ResponseEntity<String> get(String path) {
            return exchange(path, HttpMethod.GET, null);
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

            response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of()).forEach(this::store);
            return response;
        }

        private void store(String setCookie) {
            String pair = setCookie.split(";", 2)[0];
            String[] parts = pair.split("=", 2);
            if (parts[1].isEmpty()) {
                cookies.remove(parts[0]);
            } else {
                cookies.put(parts[0], parts[1]);
            }
        }
    }
}
