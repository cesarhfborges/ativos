package br.com.chfb.ativo.assetmanagement.dto;

import java.math.BigDecimal;

/** O que o usuário espera do ativo. Campos nulos significam que ele não definiu aquele preço. */
public record WatchlistPlan(BigDecimal buyBelow, BigDecimal sellAbove, BigDecimal stopLoss, String note) {
}
