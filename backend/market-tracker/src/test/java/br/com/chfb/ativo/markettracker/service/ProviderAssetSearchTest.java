package br.com.chfb.ativo.markettracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.chfb.ativo.markettracker.provider.CryptoMarketProvider;
import br.com.chfb.ativo.markettracker.provider.StockMarketProvider;
import br.com.chfb.ativo.sharedkernel.market.AssetSearchResult;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProviderAssetSearchTest {

    private final CryptoMarketProvider crypto = mock(CryptoMarketProvider.class);
    private final StockMarketProvider stock = mock(StockMarketProvider.class);
    private final ProviderAssetSearch search = new ProviderAssetSearch(Optional.of(crypto), Optional.of(stock));

    @Test
    void mesmaPesquisaSoConsultaOProvedorUmaVez() {
        when(stock.searchStocks("BBSE", 10)).thenReturn(AssetSearchResult.found(List.of()));

        search.search("BBSE", AssetClass.STOCK);
        search.search(" bbse ", AssetClass.STOCK);

        verify(stock, times(1)).searchStocks("BBSE", 10);
    }

    @Test
    void cadaTipoTemASuaPesquisa() {
        when(stock.searchStocks("BT", 10)).thenReturn(AssetSearchResult.found(List.of()));
        when(crypto.searchCrypto("BT", 10)).thenReturn(AssetSearchResult.found(List.of()));

        search.search("BT", AssetClass.STOCK);
        search.search("BT", AssetClass.CRYPTO);

        verify(stock).searchStocks("BT", 10);
        verify(crypto).searchCrypto("BT", 10);
    }

    @Test
    void falhaDoProvedorNaoFicaGuardada() {
        when(stock.searchStocks("PETR", 10))
                .thenReturn(AssetSearchResult.unavailable())
                .thenReturn(AssetSearchResult.found(List.of()));

        assertThat(search.search("PETR", AssetClass.STOCK).available()).isFalse();
        assertThat(search.search("PETR", AssetClass.STOCK).available()).isTrue();
    }
}
