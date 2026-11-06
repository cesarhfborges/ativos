package br.com.chfb.ativo.coreapi.dto.admin;

import java.time.Instant;
import java.util.List;

/**
 * Só totais: o admin não vê posições nem operações de outros usuários.
 * {@code lastCollectedAt} é a coleta mais recente desde que a aplicação iniciou.
 */
public record AdminOverviewResponse(
        long totalUsers,
        long activeUsers,
        long admins,
        long wallets,
        long transactions,
        boolean collectionEnabled,
        Instant lastCollectedAt,
        int activeSymbols,
        List<String> symbolsWithoutQuote,
        List<String> symbolsWithStaleQuote) {
}
