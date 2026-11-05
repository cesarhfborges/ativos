package br.com.chfb.ativo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class OpenApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void documentacaoEPublicaEListaTodosOsEndpointsDeAutenticacao() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(
                "\"/api/auth/csrf\"",
                "\"/api/auth/register\"",
                "\"/api/auth/login\"",
                "\"/api/auth/me\"",
                "\"/api/auth/logout\"");
    }

    @Test
    void documentacaoListaOsEndpointsDeCarteiraETransacoes() {
        String body = restTemplate.getForObject("/v3/api-docs", String.class);

        assertThat(body).contains(
                "\"/api/wallets/summary\"",
                "\"/api/wallets/fiat-movement\"",
                "\"/api/transactions\"",
                "\"/api/transactions/buy\"",
                "\"/api/transactions/sell\"");
    }

    @Test
    void documentacaoNaoExpoeDetalhesInternos() {
        String body = restTemplate.getForObject("/v3/api-docs", String.class);

        assertThat(body).doesNotContain("passwordWithinBcryptLimit", "passwordHash", "/actuator");
    }

    @Test
    void swaggerUiEPublico() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
