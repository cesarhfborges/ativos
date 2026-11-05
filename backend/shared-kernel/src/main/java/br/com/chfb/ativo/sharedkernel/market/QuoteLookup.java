package br.com.chfb.ativo.sharedkernel.market;

import java.util.Collection;
import java.util.Map;

public interface QuoteLookup {

    /** Última cotação conhecida por símbolo; símbolos nunca cotados ficam de fora do mapa. */
    Map<String, Quote> findBySymbols(Collection<String> symbols);
}
