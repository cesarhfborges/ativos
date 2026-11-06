package br.com.chfb.ativo.coreapi.controller;

import br.com.chfb.ativo.assetmanagement.dto.WatchlistItemResponse;
import br.com.chfb.ativo.assetmanagement.service.WatchlistService;
import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.coreapi.dto.AddWatchlistItemRequest;
import br.com.chfb.ativo.coreapi.dto.WatchlistPlanRequest;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watchlist")
@Tag(name = OpenApiConfig.WATCHLIST_TAG)
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @Operation(summary = "Lista os ativos acompanhados, com o plano, os dados de mercado e a posição na carteira")
    @ApiResponse(responseCode = "200", description = "Ativos acompanhados")
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping
    public List<WatchlistItemResponse> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return watchlistService.list(user.getId(), days);
    }

    @Operation(
            summary = "Passa a acompanhar um ativo",
            description = "Não altera a carteira. Na primeira vez em que o sistema vê o símbolo, ele é conferido no provedor de cotações.")
    @ApiResponse(responseCode = "201", description = "Ativo adicionado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content)
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @ApiResponse(responseCode = "409", description = "Já acompanhado ou cadastrado com outro tipo", content = @Content)
    @ApiResponse(responseCode = "422", description = "Símbolo não encontrado, limite atingido ou plano incoerente", content = @Content)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistItemResponse add(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddWatchlistItemRequest body) {
        return watchlistService.add(user.getId(), body.toOrder());
    }

    @Operation(summary = "Altera o plano de um ativo acompanhado")
    @ApiResponse(responseCode = "200", description = "Plano alterado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content)
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @ApiResponse(responseCode = "404", description = "Item não encontrado", content = @Content)
    @ApiResponse(responseCode = "422", description = "Plano incoerente", content = @Content)
    @PutMapping("/{id}")
    public WatchlistItemResponse update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody WatchlistPlanRequest body) {
        return watchlistService.update(user.getId(), id, body.toPlan());
    }

    @Operation(summary = "Deixa de acompanhar um ativo", description = "Apaga o plano. A carteira não muda.")
    @ApiResponse(responseCode = "204", description = "Ativo removido do acompanhamento")
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @ApiResponse(responseCode = "404", description = "Item não encontrado", content = @Content)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        watchlistService.remove(user.getId(), id);
    }
}
