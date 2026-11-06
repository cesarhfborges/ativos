package br.com.chfb.ativo.coreapi.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.chfb.ativo.assetmanagement.service.WalletService;
import br.com.chfb.ativo.sharedkernel.market.AssetCandidate;
import br.com.chfb.ativo.sharedkernel.market.AssetSearch;
import br.com.chfb.ativo.sharedkernel.market.AssetSearchResult;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssetControllerTest {

    private final AssetSearch assetSearch = mock(AssetSearch.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AssetController(mock(WalletService.class), assetSearch)).build();

    @Test
    void devolveOsAtivosDoProvedorNoTipoPedido() throws Exception {
        when(assetSearch.search("bitcoin", AssetClass.CRYPTO)).thenReturn(AssetSearchResult.found(List.of(
                new AssetCandidate("BTC", "Bitcoin", AssetClass.CRYPTO, new BigDecimal("417870.5")))));

        mvc.perform(get("/api/assets/lookup").param("query", "bitcoin").param("type", "CRYPTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.candidates[0].symbol").value("BTC"))
                .andExpect(jsonPath("$.candidates[0].name").value("Bitcoin"))
                .andExpect(jsonPath("$.candidates[0].assetType").value("CRYPTO"))
                .andExpect(jsonPath("$.candidates[0].price").value(417870.5));
    }

    @Test
    void avisaQuandoOProvedorNaoResponde() throws Exception {
        when(assetSearch.search("PETR", AssetClass.STOCK)).thenReturn(AssetSearchResult.unavailable());

        mvc.perform(get("/api/assets/lookup").param("query", "PETR").param("type", "STOCK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.candidates").isEmpty());
    }

    @Test
    void recusaTipoInvalido() throws Exception {
        mvc.perform(get("/api/assets/lookup").param("query", "PETR").param("type", "FUND"))
                .andExpect(status().isBadRequest());
    }
}
