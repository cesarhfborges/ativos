package br.com.chfb.ativo.markettracker.provider;

import java.math.BigDecimal;
import java.util.Map;

/** Resultado de uma consulta de preços: quem falhou mantém a última cotação conhecida, e o motivo vai para o registro. */
public record PriceFetch(Map<String, BigDecimal> prices, Map<String, String> failures) {
}
