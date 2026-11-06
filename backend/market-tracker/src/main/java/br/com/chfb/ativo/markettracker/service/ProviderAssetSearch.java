package br.com.chfb.ativo.markettracker.service;

import br.com.chfb.ativo.markettracker.provider.CryptoMarketProvider;
import br.com.chfb.ativo.markettracker.provider.StockMarketProvider;
import br.com.chfb.ativo.sharedkernel.market.AssetSearch;
import br.com.chfb.ativo.sharedkernel.market.AssetSearchResult;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Com o provedor do grupo desligado, a busca fica indisponível, e não sem resultados. A mesma pesquisa, de
 * qualquer usuário, é respondida do cache por alguns minutos, para não gastar a cota das APIs externas.
 */
@Service
public class ProviderAssetSearch implements AssetSearch {

    private static final int LIMIT = 10;
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_ENTRIES = 1000;

    private final Map<Key, Cached> cache = new ConcurrentHashMap<>();

    private final Optional<CryptoMarketProvider> cryptoProvider;
    private final Optional<StockMarketProvider> stockProvider;

    public ProviderAssetSearch(
            Optional<CryptoMarketProvider> cryptoProvider, Optional<StockMarketProvider> stockProvider) {
        this.cryptoProvider = cryptoProvider;
        this.stockProvider = stockProvider;
    }

    /** Só guarda respostas válidas: uma falha do provedor não pode virar "sem resultados" por minutos. */
    @Override
    public AssetSearchResult search(String query, AssetClass assetClass) {
        String term = query.strip();
        Key key = new Key(term.toLowerCase(Locale.ROOT), assetClass);
        Cached cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.result();
        }
        AssetSearchResult result = assetClass == AssetClass.CRYPTO
                ? cryptoProvider.map(provider -> provider.searchCrypto(term, LIMIT)).orElseGet(AssetSearchResult::unavailable)
                : stockProvider.map(provider -> provider.searchStocks(term, LIMIT)).orElseGet(AssetSearchResult::unavailable);
        if (result.available()) {
            if (cache.size() >= MAX_ENTRIES) {
                cache.clear();
            }
            cache.put(key, new Cached(result, Instant.now().plus(TTL)));
        }
        return result;
    }

    private record Key(String term, AssetClass assetClass) {
    }

    private record Cached(AssetSearchResult result, Instant expiresAt) {
    }
}
