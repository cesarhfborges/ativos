package br.com.chfb.ativo.assetmanagement.dto;

import java.math.BigDecimal;

public record SellOrder(String symbol, BigDecimal quantity, BigDecimal unitPrice, BigDecimal fee) {
}
