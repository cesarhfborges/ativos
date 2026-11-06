package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code name} é usado só no primeiro cadastro do ativo; sem {@code executedAt}, a operação é de agora.
 * {@code acceptUnverified} cadastra o ativo mesmo que o provedor de cotações não o encontre.
 */
public record BuyOrder(
        String symbol,
        String name,
        AssetType assetType,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal fee,
        Instant executedAt,
        boolean acceptUnverified) {
}
