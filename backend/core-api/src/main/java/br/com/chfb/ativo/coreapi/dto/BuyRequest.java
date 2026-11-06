package br.com.chfb.ativo.coreapi.dto;

import br.com.chfb.ativo.assetmanagement.dto.BuyOrder;
import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record BuyRequest(
        @Schema(example = "BTC")
        @NotNull
        @Pattern(regexp = "[A-Za-z0-9._-]{1,20}")
        String symbol,

        @Schema(example = "Bitcoin", description = "Usado só no primeiro cadastro do ativo. Sem ele, vale o símbolo.")
        @Size(max = 120)
        String name,

        @NotNull
        AssetType assetType,

        @Schema(example = "0.5")
        @NotNull
        @Positive
        @Digits(integer = 16, fraction = 8)
        BigDecimal quantity,

        @Schema(example = "100.00")
        @NotNull
        @Positive
        @Digits(integer = 16, fraction = 8)
        BigDecimal unitPrice,

        @Schema(example = "1.50", description = "Sem valor, a taxa é zero.")
        @PositiveOrZero
        @Digits(integer = 14, fraction = 4)
        BigDecimal fee,

        @Schema(description = "Quando a operação aconteceu. Sem valor, é agora.")
        @PastOrPresent(message = "A data da operação não pode estar no futuro")
        Instant executedAt,

        @Schema(description = "Cadastra o ativo mesmo que o provedor de cotações não o encontre.")
        Boolean acceptUnverified) {

    public BuyOrder toOrder() {
        return new BuyOrder(
                symbol,
                name,
                assetType,
                quantity,
                unitPrice,
                fee == null ? BigDecimal.ZERO : fee,
                executedAt,
                Boolean.TRUE.equals(acceptUnverified));
    }
}
