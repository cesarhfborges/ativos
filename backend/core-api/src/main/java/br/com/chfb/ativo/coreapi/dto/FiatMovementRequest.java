package br.com.chfb.ativo.coreapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record FiatMovementRequest(
        @NotNull
        FiatMovementType type,

        @Schema(example = "1000.00")
        @NotNull
        @Positive
        @Digits(integer = 14, fraction = 4)
        BigDecimal amount) {

    public enum FiatMovementType {
        DEPOSIT,
        WITHDRAW
    }
}
