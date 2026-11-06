package br.com.chfb.ativo.coreapi.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Com o módulo de projeções desligado, o sinal some e o acompanhamento continua funcionando. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"predictive.enabled=false", "market.provider.crypto=", "market.provider.stock=", "market.collect-on-startup=false"})
class TechnicalSignalsDisabledIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    private final Map<String, String> cookies = new HashMap<>();

    @Test
    void semOModuloOSinalFicaNulo() throws Exception {
        String email = "off-" + UUID.randomUUID() + "@example.com";
        exchange("/api/auth/csrf", HttpMethod.GET, null);
        exchange("/api/auth/register", HttpMethod.POST, """
                {"name":"Ana","email":"%s","password":"senha-forte-123","confirmPassword":"senha-forte-123"}""".formatted(email));
        exchange("/api/auth/login", HttpMethod.POST, """
                {"email":"%s","password":"senha-forte-123"}""".formatted(email));

        ResponseEntity<String> added = exchange("/api/watchlist", HttpMethod.POST, """
                {"symbol":"OFF1","assetType":"CRYPTO","acceptUnverified":true}""");

        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode list = new ObjectMapper().readTree(exchange("/api/watchlist", HttpMethod.GET, null).getBody());
        assertThat(list.at("/0/symbol").asText()).isEqualTo("OFF1");
        assertThat(list.at("/0/signal").isNull()).isTrue();
        assertThat(exchange("/api/dashboard/sentiment", HttpMethod.GET, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
