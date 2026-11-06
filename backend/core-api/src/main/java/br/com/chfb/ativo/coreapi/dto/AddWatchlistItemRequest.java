package br.com.chfb.ativo.coreapi.dto;

import br.com.chfb.ativo.assetmanagement.dto.WatchlistItemOrder;
import br.com.chfb.ativo.assetmanagement.dto.WatchlistPlan;
import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AddWatchlistItemRequest(
        @Schema(example = "BTC")
        @NotNull
        @Pattern(regexp = "[A-Za-z0-9._-]{1,20}")
        String symbol,

        @Schema(example = "Bitcoin", description = "Usado só no primeiro cadastro do ativo. Sem ele, vale o símbolo.")
        @Size(max = 120)
        String name,

        @NotNull
        AssetType assetType,

        @Positive
        @Digits(integer = 16, fraction = 8)
        BigDecimal buyBelow,

        @Positive
        @Digits(integer = 16, fraction = 8)
        BigDecimal sellAbove,

        @Positive
        @Digits(integer = 16, fraction = 8)
        BigDecimal stopLoss,

        @Size(max = 500)
        String note,

        @Schema(description = "Cadastra o ativo mesmo que o provedor de cotações não o encontre.")
        Boolean acceptUnverified) {

    public WatchlistItemOrder toOrder() {
        return new WatchlistItemOrder(
                symbol,
                name,
                assetType,
                new WatchlistPlan(buyBelow, sellAbove, stopLoss, note),
                Boolean.TRUE.equals(acceptUnverified));
    }
}
