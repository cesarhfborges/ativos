package br.com.chfb.ativo.coreapi.dto;

import br.com.chfb.ativo.assetmanagement.dto.WatchlistPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record WatchlistPlanRequest(
        @Schema(example = "30.00", description = "Comprar quando o preço chegar a este valor ou menos.")
        @Positive
        @Digits(integer = 16, fraction = 8)
        BigDecimal buyBelow,

        @Schema(example = "42.00", description = "Vender quando o preço chegar a este valor ou mais.")
        @Positive
        @Digits(integer = 16, fraction = 8)
        BigDecimal sellAbove,

        @Schema(example = "25.00", description = "Sair para limitar a perda se o preço cair a este valor ou menos.")
        @Positive
        @Digits(integer = 16, fraction = 8)
        BigDecimal stopLoss,

        @Schema(example = "Vender antes do balanço")
        @Size(max = 500)
        String note) {

    public WatchlistPlan toPlan() {
        return new WatchlistPlan(buyBelow, sellAbove, stopLoss, note);
    }
}
