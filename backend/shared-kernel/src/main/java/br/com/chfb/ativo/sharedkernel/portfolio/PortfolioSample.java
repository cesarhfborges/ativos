package br.com.chfb.ativo.sharedkernel.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

/** Ponto do histórico: {@code invested} é o custo das posições e {@code total}, o valor de mercado. */
public record PortfolioSample(Instant time, BigDecimal invested, BigDecimal total) {
}
