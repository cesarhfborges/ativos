package br.com.chfb.ativo.markettracker.service;

import br.com.chfb.ativo.markettracker.provider.CryptoMarketProvider;
import br.com.chfb.ativo.markettracker.provider.StockMarketProvider;
import br.com.chfb.ativo.sharedkernel.market.AssetSearch;
import br.com.chfb.ativo.sharedkernel.market.AssetSearchResult;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Com o provedor do grupo desligado, a busca fica indisponível, e não sem resultados. */
@Service
public class ProviderAssetSearch implements AssetSearch {

    private static final int LIMIT = 10;

    private final Optional<CryptoMarketProvider> cryptoProvider;
    private final Optional<StockMarketProvider> stockProvider;

    public ProviderAssetSearch(
            Optional<CryptoMarketProvider> cryptoProvider, Optional<StockMarketProvider> stockProvider) {
        this.cryptoProvider = cryptoProvider;
        this.stockProvider = stockProvider;
    }

    @Override
    public AssetSearchResult search(String query, AssetClass assetClass) {
        String term = query.strip();
        return assetClass == AssetClass.CRYPTO
                ? cryptoProvider.map(provider -> provider.searchCrypto(term, LIMIT)).orElseGet(AssetSearchResult::unavailable)
                : stockProvider.map(provider -> provider.searchStocks(term, LIMIT)).orElseGet(AssetSearchResult::unavailable);
    }
}
