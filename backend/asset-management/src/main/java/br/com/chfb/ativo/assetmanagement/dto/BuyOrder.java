package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import java.math.BigDecimal;

/** {@code name} é usado só no primeiro cadastro do ativo; sem ele, vale o símbolo. */
public record BuyOrder(
        String symbol,
        String name,
        AssetType assetType,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal fee,
        boolean autoDeposit) {
}
