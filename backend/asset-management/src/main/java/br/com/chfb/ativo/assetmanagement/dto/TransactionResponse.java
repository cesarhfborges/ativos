package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.assetmanagement.entity.TransactionType;
import br.com.chfb.ativo.assetmanagement.entity.WalletTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** {@code realizedProfit} só vem preenchido nas vendas; {@code reversedAt}, nas operações estornadas. */
public record TransactionResponse(
        UUID id,
        TransactionType type,
        String symbol,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal fee,
        BigDecimal totalAmount,
        BigDecimal realizedProfit,
        Instant timestamp,
        Instant reversedAt) {

    public static TransactionResponse from(WalletTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAsset().getSymbol(),
                transaction.getQuantity(),
                transaction.getUnitPrice(),
                transaction.getFee(),
                transaction.getTotalAmount(),
                transaction.getRealizedProfit(),
                transaction.getTimestamp(),
                transaction.getReversedAt());
    }
}
