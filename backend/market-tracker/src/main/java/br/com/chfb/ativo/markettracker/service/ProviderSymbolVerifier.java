package br.com.chfb.ativo.markettracker.service;

import br.com.chfb.ativo.markettracker.provider.CryptoMarketProvider;
import br.com.chfb.ativo.markettracker.provider.StockMarketProvider;
import br.com.chfb.ativo.sharedkernel.market.SymbolCheck;
import br.com.chfb.ativo.sharedkernel.market.SymbolVerifier;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Com o provedor do grupo desligado, não há como confirmar nem negar o símbolo. */
@Service
public class ProviderSymbolVerifier implements SymbolVerifier {

    private final Optional<CryptoMarketProvider> cryptoProvider;
    private final Optional<StockMarketProvider> stockProvider;

    public ProviderSymbolVerifier(
            Optional<CryptoMarketProvider> cryptoProvider, Optional<StockMarketProvider> stockProvider) {
        this.cryptoProvider = cryptoProvider;
        this.stockProvider = stockProvider;
    }

    @Override
    public SymbolCheck verify(String symbol, AssetClass assetClass) {
        return assetClass == AssetClass.CRYPTO
                ? cryptoProvider.map(provider -> provider.checkCryptoSymbol(symbol)).orElse(SymbolCheck.UNAVAILABLE)
                : stockProvider.map(provider -> provider.checkStockSymbol(symbol)).orElse(SymbolCheck.UNAVAILABLE);
    }
}
