package br.com.chfb.ativo.coreapi.controller;

import br.com.chfb.ativo.assetmanagement.dto.TransactionResponse;
import br.com.chfb.ativo.assetmanagement.entity.TransactionType;
import br.com.chfb.ativo.assetmanagement.service.WalletService;
import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.coreapi.dto.BuyRequest;
import br.com.chfb.ativo.coreapi.dto.SellRequest;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = OpenApiConfig.TRANSACTION_TAG)
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class TransactionController {

    private final WalletService walletService;

    public TransactionController(WalletService walletService) {
        this.walletService = walletService;
    }

    @Operation(summary = "Retorna o histórico de compras e vendas do usuário logado, do mais recente ao mais antigo")
    @ApiResponse(responseCode = "200", description = "Página do extrato")
    @ApiResponse(
            responseCode = "400",
            description = "Paginação inválida",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping
    public List<TransactionResponse> statement(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return walletService.statement(user.getId(), type, page, size);
    }

    @Operation(
            summary = "Registra uma compra",
            description = "Cadastra o ativo na primeira compra e recalcula o preço médio da posição.")
    @ApiResponse(responseCode = "201", description = "Compra registrada")
    @ApiResponse(
            responseCode = "400",
            description = "Dados inválidos",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @ApiResponse(
            responseCode = "409",
            description = "Símbolo já cadastrado com outro tipo de ativo",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "422",
            description = "Valor acima do limite",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/buy")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse buy(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody BuyRequest body) {
        return walletService.buy(user.getId(), body.toOrder());
    }

    @Operation(summary = "Registra uma venda", description = "Reduz a custódia e registra o valor líquido.")
    @ApiResponse(responseCode = "201", description = "Venda registrada")
    @ApiResponse(
            responseCode = "400",
            description = "Dados inválidos",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @ApiResponse(
            responseCode = "422",
            description = "Custódia insuficiente, taxa maior que a venda ou valor acima do limite",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/sell")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse sell(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SellRequest body) {
        return walletService.sell(user.getId(), body.toOrder());
    }
}
