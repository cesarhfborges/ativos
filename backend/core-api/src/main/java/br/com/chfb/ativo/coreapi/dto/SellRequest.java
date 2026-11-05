package br.com.chfb.ativo.coreapi.dto;

import br.com.chfb.ativo.assetmanagement.dto.SellOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record SellRequest(
        @Schema(example = "BTC")
        @NotNull
        @Pattern(regexp = "[A-Za-z0-9._-]{1,20}")
        String symbol,

        @Schema(example = "0.25")
        @NotNull
        @Positive
        @Digits(integer = 16, fraction = 8)
        BigDecimal quantity,

        @Schema(example = "120.00")
        @NotNull
        @Positive
        @Digits(integer = 14, fraction = 4)
        BigDecimal unitPrice,

        @Schema(example = "1.50", description = "Sem valor, a taxa é zero.")
        @PositiveOrZero
        @Digits(integer = 14, fraction = 4)
        BigDecimal fee) {

    public SellOrder toOrder() {
        return new SellOrder(symbol, quantity, unitPrice, fee == null ? BigDecimal.ZERO : fee);
    }
}
