package br.com.chfb.ativo.assetmanagement.service;

import br.com.chfb.ativo.assetmanagement.dto.DashboardPositionResponse;
import br.com.chfb.ativo.assetmanagement.entity.WalletPosition;
import br.com.chfb.ativo.sharedkernel.market.Quote;
import br.com.chfb.ativo.sharedkernel.market.QuoteLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Avalia posições pela última cotação. Usa a escala de dinheiro, mas sem o limite das colunas de caixa. */
@Component
public class PortfolioValuation {

    private static final int MONEY_SCALE = 4;
    private static final int PERCENT_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final QuoteLookup quoteLookup;

    public PortfolioValuation(QuoteLookup quoteLookup) {
        this.quoteLookup = quoteLookup;
    }

    public List<DashboardPositionResponse> value(Collection<WalletPosition> positions) {
        Map<String, Quote> quotes = quoteLookup.findBySymbols(
                positions.stream().map(position -> position.getAsset().getSymbol()).toList());
        return positions.stream().map(position -> value(position, quotes.get(position.getAsset().getSymbol()))).toList();
    }

    /** Retorno (%) = (valor a mercado - valor investido) / valor investido × 100. */
    public static BigDecimal percent(BigDecimal profitLoss, BigDecimal invested) {
        if (invested.signum() == 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE);
        }
        return profitLoss.multiply(HUNDRED).divide(invested, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static DashboardPositionResponse value(WalletPosition position, Quote quote) {
        BigDecimal invested = money(position.getQuantity().multiply(position.getAveragePrice()));
        BigDecimal market = quote == null ? invested : money(position.getQuantity().multiply(quote.price()));
        BigDecimal profitLoss = market.subtract(invested);
        return new DashboardPositionResponse(
                position.getAsset().getSymbol(),
                position.getAsset().getName(),
                position.getAsset().getAssetType(),
                position.getQuantity(),
                position.getAveragePrice(),
                quote == null ? null : quote.price(),
                invested,
                market,
                profitLoss,
                percent(profitLoss, invested),
                quote == null ? null : quote.lastUpdatedAt());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
