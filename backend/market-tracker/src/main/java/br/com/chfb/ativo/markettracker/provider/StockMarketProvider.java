package br.com.chfb.ativo.markettracker.provider;

import br.com.chfb.ativo.sharedkernel.market.AssetSearchResult;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.SymbolCheck;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A implementação ativa é escolhida por {@code market.provider.stock}. */
public interface StockMarketProvider {

    /** Pode devolver menos símbolos que os pedidos: os que falharem mantêm a última cotação conhecida. */
    Map<String, BigDecimal> fetchStockPrices(Set<String> symbols);

    /** Fechamentos diários do maior período disponível, só de dias encerrados. Vazio se o provedor não tiver o símbolo. */
    List<PricePoint> fetchStockDailyCloses(String symbol);

    /** {@code NOT_FOUND} só quando o provedor afirma que o símbolo não existe; qualquer outra falha é {@code UNAVAILABLE}. */
    SymbolCheck checkStockSymbol(String symbol);

    /** Procura por símbolo ou nome. Falhas do provedor voltam como indisponível, nunca como exceção. */
    AssetSearchResult searchStocks(String query, int limit);
}
