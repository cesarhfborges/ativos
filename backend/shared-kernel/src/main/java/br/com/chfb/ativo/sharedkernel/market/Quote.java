package br.com.chfb.ativo.sharedkernel.market;

import java.math.BigDecimal;
import java.time.Instant;

public record Quote(String symbol, BigDecimal price, Instant lastUpdatedAt) {
}
