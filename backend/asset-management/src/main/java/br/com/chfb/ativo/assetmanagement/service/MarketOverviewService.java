package br.com.chfb.ativo.assetmanagement.service;

import br.com.chfb.ativo.assetmanagement.dto.MarketAssetResponse;
import br.com.chfb.ativo.assetmanagement.repository.WalletPositionRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletRepository;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.PriceResolution;
import br.com.chfb.ativo.sharedkernel.market.Quote;
import br.com.chfb.ativo.sharedkernel.market.QuoteLookup;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O mercado visto pelos ativos em carteira, independentemente de quanto o usuário tem de cada um. */
@Service
public class MarketOverviewService {

    /** Até aqui o gráfico tem espaço para um ponto por hora; acima, um por dia já mostra a tendência. */
    private static final int MAX_DAYS_HOURLY = 7;

    private final WalletRepository walletRepository;
    private final WalletPositionRepository positionRepository;
    private final QuoteLookup quoteLookup;

    public MarketOverviewService(
            WalletRepository walletRepository, WalletPositionRepository positionRepository, QuoteLookup quoteLookup) {
        this.walletRepository = walletRepository;
        this.positionRepository = positionRepository;
        this.quoteLookup = quoteLookup;
    }

    @Transactional(readOnly = true)
    public List<MarketAssetResponse> overview(UUID userId, int days) {
        List<String> symbols = walletRepository.findByUserId(userId)
                .map(wallet -> positionRepository.findActiveByWalletId(wallet.getId()).stream()
                        .map(position -> position.getAsset().getSymbol())
                        .toList())
                .orElseGet(List::of);
        return List.copyOf(bySymbols(symbols, days).values());
    }

    /** Os dados de mercado de quaisquer símbolos, na ordem pedida. Todos aparecem, mesmo sem nenhum preço. */
    @Transactional(readOnly = true)
    public Map<String, MarketAssetResponse> bySymbols(List<String> symbols, int days) {
        Instant now = Instant.now();
        PriceResolution resolution = days <= MAX_DAYS_HOURLY ? PriceResolution.HOUR : PriceResolution.DAY;
        Map<String, Quote> quotes = quoteLookup.findBySymbols(symbols);
        Map<String, BigDecimal> prices24hAgo = quoteLookup.findPricesAt(symbols, now.minus(Duration.ofDays(1)));
        Map<String, List<PricePoint>> history =
                quoteLookup.findPriceHistory(symbols, now.minus(Duration.ofDays(days)), resolution);

        Map<String, MarketAssetResponse> market = new LinkedHashMap<>();
        symbols.forEach(symbol -> market.put(symbol, MarketAssetResponse.of(
                symbol, quotes.get(symbol), prices24hAgo.get(symbol), history.getOrDefault(symbol, List.of()))));
        return market;
    }
}
