package br.com.chfb.ativo.sharedkernel.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface QuoteLookup {

    /** Última cotação conhecida por símbolo; símbolos nunca cotados ficam de fora do mapa. */
    Map<String, Quote> findBySymbols(Collection<String> symbols);

    /** Preços desde {@code from}, do mais antigo ao mais recente; símbolos sem histórico ficam de fora do mapa. */
    Map<String, List<PricePoint>> findPriceHistory(Collection<String> symbols, Instant from, PriceResolution resolution);

    /** Último preço registrado até {@code at}; símbolos sem registro até lá ficam de fora do mapa. */
    Map<String, BigDecimal> findPricesAt(Collection<String> symbols, Instant at);
}
