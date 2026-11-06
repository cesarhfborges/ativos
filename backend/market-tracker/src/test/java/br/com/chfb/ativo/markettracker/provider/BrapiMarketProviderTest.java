package br.com.chfb.ativo.markettracker.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.chfb.ativo.sharedkernel.market.AssetCandidate;
import br.com.chfb.ativo.sharedkernel.market.AssetSearchResult;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BrapiMarketProviderTest {

    private MockRestServiceServer server;
    private BrapiMarketProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new BrapiMarketProvider(builder.build(), "https://brapi.test", "");
    }

    @Test
    void trazNomeEUltimoPrecoDaListagem() {
        server.expect(requestTo(containsString("/api/quote/list?search=PETR&limit=10")))
                .andRespond(withSuccess("""
                        {"indexes":[],"stocks":[{"stock":"PETR4","name":"PETROBRAS","close":45.78,"type":"stock"},{"stock":"XPTO3","name":null,"close":null}]}""",
                        MediaType.APPLICATION_JSON));

        AssetSearchResult result = provider.searchStocks("PETR", 10);

        assertThat(result.available()).isTrue();
        assertThat(result.candidates()).containsExactly(
                new AssetCandidate("PETR4", "PETROBRAS", AssetClass.STOCK, new BigDecimal("45.78")),
                new AssetCandidate("XPTO3", "XPTO3", AssetClass.STOCK, null));
    }

    @Test
    void semResultadosNaoEIndisponivel() {
        server.expect(requestTo(containsString("/api/quote/list")))
                .andRespond(withSuccess("{\"stocks\":[]}", MediaType.APPLICATION_JSON));

        AssetSearchResult result = provider.searchStocks("ZZZZ", 10);

        assertThat(result.available()).isTrue();
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void falhaDoProvedorFicaIndisponivel() {
        server.expect(requestTo(containsString("/api/quote/list"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(provider.searchStocks("PETR", 10).available()).isFalse();
    }

    @Test
    void cotacaoQueFalhaVoltaComOMotivo() {
        server.expect(requestTo(containsString("/api/quote/BBSE3"))).andRespond(withServerError());

        PriceFetch result = provider.fetchStockPrices(java.util.Set.of("BBSE3"));

        assertThat(result.prices()).isEmpty();
        assertThat(result.failures().get("BBSE3")).contains("500");
    }
}
