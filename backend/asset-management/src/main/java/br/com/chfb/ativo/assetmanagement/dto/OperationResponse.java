package br.com.chfb.ativo.assetmanagement.dto;

import java.math.BigDecimal;

/** {@code autoDeposit} só vem preenchido quando a compra precisou gerar um depósito automático. */
public record OperationResponse(
        TransactionResponse transaction, TransactionResponse autoDeposit, BigDecimal fiatBalance) {
}
