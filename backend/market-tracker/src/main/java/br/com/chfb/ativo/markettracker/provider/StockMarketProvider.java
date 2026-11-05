package br.com.chfb.ativo.markettracker.provider;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/** A implementação ativa é escolhida por {@code market.provider.stock}. */
public interface StockMarketProvider {

    /** Pode devolver menos símbolos que os pedidos: os que falharem mantêm a última cotação conhecida. */
    Map<String, BigDecimal> fetchStockPrices(Set<String> symbols);
}
