package br.com.chfb.ativo.coreapi.controller;

import br.com.chfb.ativo.assetmanagement.dto.StatementFilter;
import br.com.chfb.ativo.assetmanagement.dto.StatementPage;
import br.com.chfb.ativo.assetmanagement.dto.TransactionResponse;
import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import br.com.chfb.ativo.assetmanagement.entity.TransactionStatus;
import br.com.chfb.ativo.assetmanagement.entity.TransactionType;
import br.com.chfb.ativo.assetmanagement.service.WalletService;
import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.coreapi.dto.BuyRequest;
import br.com.chfb.ativo.coreapi.dto.SellRequest;
import br.com.chfb.ativo.coreapi.dto.SimplePage;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = OpenApiConfig.TRANSACTION_TAG)
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class TransactionController {

    /** Mantém o deslocamento da consulta dentro de um {@code int} mesmo com a maior página. */
    private static final int MAX_PAGE = 10_000_000;

    private final WalletService walletService;

    public TransactionController(WalletService walletService) {
        this.walletService = walletService;
    }

    @Operation(
            summary = "Retorna o histórico de compras e vendas do usuário logado, do mais recente ao mais antigo",
            description = "Paginado como o simplePaginate do Laravel: sem total, o que dispensa a contagem de registros.")
    @ApiResponse(responseCode = "200", description = "Página do extrato")
    @ApiResponse(
            responseCode = "400",
            description = "Paginação ou filtro inválido",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping
    public SimplePage<TransactionResponse> statement(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Parte do símbolo ou do nome do ativo") @RequestParam(required = false) @Size(max = 120) String asset,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) AssetType assetType,
            @RequestParam(required = false) TransactionStatus status,
            @Parameter(description = "Data da operação, a partir deste instante (ISO 8601)") @RequestParam(required = false) Instant from,
            @Parameter(description = "Data da operação, até este instante, inclusive (ISO 8601)") @RequestParam(required = false) Instant to,
            @RequestParam(required = false) @PositiveOrZero BigDecimal minTotal,
            @RequestParam(required = false) @PositiveOrZero BigDecimal maxTotal,
            @RequestParam(defaultValue = "1") @Min(1) @Max(MAX_PAGE) int page,
            @RequestParam(name = "per_page", defaultValue = "50") @Min(1) @Max(200) int perPage,
            HttpServletRequest request) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data inicial não pode ser depois da final");
        }
        if (minTotal != null && maxTotal != null && minTotal.compareTo(maxTotal) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor mínimo não pode ser maior que o máximo");
        }
        StatementFilter filter = new StatementFilter(asset, type, assetType, status, from, to, minTotal, maxTotal);
        StatementPage statement = walletService.statement(user.getId(), filter, page, perPage);
        return SimplePage.of(statement.items(), statement.hasMore(), page, perPage, request);
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

    @Operation(
            summary = "Estorna uma compra ou venda",
            description = "A operação continua no histórico, marcada como estornada, e a posição é refeita a partir "
                    + "das demais. O lucro realizado das vendas posteriores pode mudar.")
    @ApiResponse(responseCode = "200", description = "Operação estornada")
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Operação não encontrada",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "422",
            description = "Já estornada, ou uma venda posterior ficaria sem custódia",
            content = @Content(
                    mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/{id}/reverse")
    public TransactionResponse reverse(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return walletService.reverse(user.getId(), id);
    }
}
