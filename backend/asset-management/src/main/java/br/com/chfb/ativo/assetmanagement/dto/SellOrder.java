package br.com.chfb.ativo.assetmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Sem {@code executedAt}, a operação é de agora. */
public record SellOrder(String symbol, BigDecimal quantity, BigDecimal unitPrice, BigDecimal fee, Instant executedAt) {
}
