package br.com.chfb.ativo.coreapi.controller;

import br.com.chfb.ativo.assetmanagement.dto.OperationResponse;
import br.com.chfb.ativo.assetmanagement.dto.WalletSummaryResponse;
import br.com.chfb.ativo.assetmanagement.service.WalletService;
import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.coreapi.dto.FiatMovementRequest;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallets")
@Tag(name = OpenApiConfig.WALLET_TAG)
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @Operation(summary = "Retorna o saldo em caixa e as posições do usuário logado")
    @ApiResponse(responseCode = "200", description = "Resumo da carteira")
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping("/summary")
    public WalletSummaryResponse summary(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return walletService.summary(user.getId());
    }

    @Operation(summary = "Registra um aporte ou um resgate de dinheiro")
    @ApiResponse(responseCode = "201", description = "Movimentação registrada")
    @ApiResponse(
            responseCode = "400",
            description = "Dados inválidos",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @ApiResponse(
            responseCode = "422",
            description = "Saldo em caixa insuficiente ou valor acima do limite",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/fiat-movement")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationResponse fiatMovement(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody FiatMovementRequest body) {
        return switch (body.type()) {
            case DEPOSIT -> walletService.deposit(user.getId(), body.amount());
            case WITHDRAW -> walletService.withdraw(user.getId(), body.amount());
        };
    }
}
