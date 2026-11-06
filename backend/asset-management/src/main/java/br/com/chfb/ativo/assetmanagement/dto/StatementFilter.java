package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import br.com.chfb.ativo.assetmanagement.entity.TransactionStatus;
import br.com.chfb.ativo.assetmanagement.entity.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

/** Filtros do extrato; um campo nulo não restringe nada. {@code from} e {@code to} se referem à data da operação. */
public record StatementFilter(
        String asset,
        TransactionType type,
        AssetType assetType,
        TransactionStatus status,
        Instant from,
        Instant to,
        BigDecimal minTotal,
        BigDecimal maxTotal) {
}
