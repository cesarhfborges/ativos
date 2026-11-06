package br.com.chfb.ativo.markettracker.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.chfb.ativo.sharedkernel.market.AssetCandidate;
import br.com.chfb.ativo.sharedkernel.market.AssetSearchResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BinanceMarketProviderTest {

    private static final String EXCHANGE_INFO = """
            {"symbols":[
              {"symbol":"BTCBRL","status":"TRADING","baseAsset":"BTC","quoteAsset":"BRL"},
              {"symbol":"BNBBRL","status":"TRADING","baseAsset":"BNB","quoteAsset":"BRL"},
              {"symbol":"NEWBRL","status":"TRADING","baseAsset":"NEW","quoteAsset":"BRL"},
              {"symbol":"DOTBRL","status":"BREAK","baseAsset":"DOT","quoteAsset":"BRL"},
              {"symbol":"ETHUSDT","status":"TRADING","baseAsset":"ETH","quoteAsset":"USDT"}
            ]}""";

    private MockRestServiceServer server;
    private BinanceMarketProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new BinanceMarketProvider(builder.build(), "https://binance.test", "BRL");
    }

    @Test
    void procuraPorSimboloEPorNomeSoEntreParesNegociaveisNaMoedaCotada() {
        expectExchangeInfo();
        expectPrices("[{\"symbol\":\"BTCBRL\",\"price\":\"417488.00000000\"}]");

        AssetSearchResult result = provider.searchCrypto("bitcoin", 10);

        assertThat(result.available()).isTrue();
        assertThat(result.candidates()).extracting(AssetCandidate::symbol).containsExactly("BTC");
        assertThat(result.candidates().get(0).name()).isEqualTo("Bitcoin");
        assertThat(result.candidates().get(0).price()).isEqualByComparingTo(new BigDecimal("417488"));
        server.verify();
    }

    @Test
    void naoEncontraParDesativadoNemDeOutraMoeda() {
        expectExchangeInfo();
        assertThat(provider.searchCrypto("DOT", 10).candidates()).isEmpty();
        assertThat(provider.searchCrypto("ETH", 10).candidates()).isEmpty();
    }

    @Test
    void ativoForaDoMapaUsaOSimboloComoNome() {
        expectExchangeInfo();
        expectPrices("[{\"symbol\":\"NEWBRL\",\"price\":\"1.5\"}]");
        assertThat(provider.searchCrypto("NEW", 10).candidates().get(0).name()).isEqualTo("NEW");
    }

    @Test
    void reaproveitaAListaDePares() {
        expectExchangeInfo();
        expectPrices("[{\"symbol\":\"BTCBRL\",\"price\":\"1\"}]");
        expectPrices("[{\"symbol\":\"BTCBRL\",\"price\":\"2\"}]");

        provider.searchCrypto("BTC", 10);
        assertThat(provider.searchCrypto("BTC", 10).candidates().get(0).price()).isEqualByComparingTo("2");
        server.verify();
    }

    @Test
    void semCotacaoOAtivoApareceSemPreco() {
        expectExchangeInfo();
        server.expect(once(), requestTo(containsString("/ticker/price"))).andRespond(withServerError());

        AssetSearchResult result = provider.searchCrypto("BTC", 10);

        assertThat(result.available()).isTrue();
        assertThat(result.candidates().get(0).price()).isNull();
    }

    @Test
    void semAListaDeParesABuscaFicaIndisponivel() {
        server.expect(once(), requestTo(containsString("/exchangeInfo"))).andRespond(withServerError());
        assertThat(provider.searchCrypto("BTC", 10).available()).isFalse();
    }

    private void expectExchangeInfo() {
        server.expect(once(), requestTo(containsString("/exchangeInfo")))
                .andRespond(withSuccess(EXCHANGE_INFO, MediaType.APPLICATION_JSON));
    }

    private void expectPrices(String body) {
        server.expect(once(), requestTo(containsString("/ticker/price")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void cotacaoQueFalhaVoltaComOMotivoSemDerrubarAsOutras() {
        server.expect(requestTo(containsString("symbol=BTCBRL")))
                .andRespond(withSuccess("{\"symbol\":\"BTCBRL\",\"price\":\"100\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("symbol=XRPBRL"))).andRespond(withServerError());

        PriceFetch result = provider.fetchCryptoPrices(new java.util.LinkedHashSet<>(java.util.List.of("BTC", "XRP")));

        assertThat(result.prices()).containsOnlyKeys("BTC");
        assertThat(result.failures()).containsOnlyKeys("XRP");
        assertThat(result.failures().get("XRP")).contains("500");
    }
}
