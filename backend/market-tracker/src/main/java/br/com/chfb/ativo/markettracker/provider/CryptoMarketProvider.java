package br.com.chfb.ativo.markettracker.provider;

import br.com.chfb.ativo.sharedkernel.market.AssetSearchResult;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.SymbolCheck;
import java.util.List;
import java.util.Set;

/** A implementação ativa é escolhida por {@code market.provider.crypto}. */
public interface CryptoMarketProvider {

    /** Pode devolver menos símbolos que os pedidos: os que falharem mantêm a última cotação conhecida. */
    PriceFetch fetchCryptoPrices(Set<String> symbols);

    /** Fechamentos diários do último ano, só de dias encerrados. Vazio se o provedor não tiver o símbolo. */
    List<PricePoint> fetchCryptoDailyCloses(String symbol);

    /** {@code NOT_FOUND} só quando o provedor afirma que o símbolo não existe; qualquer outra falha é {@code UNAVAILABLE}. */
    SymbolCheck checkCryptoSymbol(String symbol);

    /** Procura por símbolo ou nome. Falhas do provedor voltam como indisponível, nunca como exceção. */
    AssetSearchResult searchCrypto(String query, int limit);
}
