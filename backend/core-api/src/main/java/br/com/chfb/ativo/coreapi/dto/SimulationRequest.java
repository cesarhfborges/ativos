package br.com.chfb.ativo.coreapi.dto;

import br.com.chfb.ativo.predictiveengine.dto.Scenario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Set;

public record SimulationRequest(
        @Schema(example = "12", allowableValues = {"6", "12", "24", "60"})
        @NotNull
        Integer months,

        @Schema(example = "500.00", description = "Compras mensais estimadas. Sem valor, é zero.")
        @PositiveOrZero
        @Digits(integer = 14, fraction = 4)
        BigDecimal monthlyContribution,

        @NotNull
        Scenario scenario) {

    private static final Set<Integer> ALLOWED_MONTHS = Set.of(6, 12, 24, 60);

    @Schema(hidden = true)
    @AssertTrue(message = "O horizonte deve ser de 6, 12, 24 ou 60 meses")
    boolean isMonthsAllowed() {
        return months == null || ALLOWED_MONTHS.contains(months);
    }

    public BigDecimal contributionOrZero() {
        return monthlyContribution == null ? BigDecimal.ZERO : monthlyContribution;
    }
}
