package br.com.chfb.ativo.coreapi.controller;

import br.com.chfb.ativo.assetmanagement.dto.DashboardOverviewResponse;
import br.com.chfb.ativo.assetmanagement.dto.DashboardPositionResponse;
import br.com.chfb.ativo.assetmanagement.dto.MarketAssetResponse;
import br.com.chfb.ativo.assetmanagement.dto.PortfolioHistoryPoint;
import br.com.chfb.ativo.assetmanagement.service.DashboardService;
import br.com.chfb.ativo.assetmanagement.service.MarketOverviewService;
import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.coreapi.dto.QuoteRefreshResponse;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import br.com.chfb.ativo.markettracker.service.QuoteCollector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = OpenApiConfig.DASHBOARD_TAG)
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class DashboardController {

    private final DashboardService dashboardService;
    private final MarketOverviewService marketOverviewService;
    private final QuoteCollector quoteCollector;

    public DashboardController(
            DashboardService dashboardService,
            MarketOverviewService marketOverviewService,
            QuoteCollector quoteCollector) {
        this.dashboardService = dashboardService;
        this.marketOverviewService = marketOverviewService;
        this.quoteCollector = quoteCollector;
    }

    @Operation(summary = "Retorna a visão consolidada: valor investido, valor a mercado e retorno")
    @ApiResponse(responseCode = "200", description = "Visão consolidada")
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping("/overview")
    public DashboardOverviewResponse overview(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return dashboardService.overview(user.getId());
    }

    @Operation(summary = "Retorna as posições com preço médio, cotação atual e lucro ou prejuízo")
    @ApiResponse(responseCode = "200", description = "Posições avaliadas")
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping("/positions")
    public List<DashboardPositionResponse> positions(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return dashboardService.positions(user.getId());
    }

    @Operation(summary = "Retorna a evolução patrimonial, a partir dos snapshots dos últimos dias")
    @ApiResponse(responseCode = "200", description = "Pontos do histórico, do mais antigo ao mais recente")
    @ApiResponse(responseCode = "400", description = "Período inválido", content = @Content)
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping("/history")
    public List<PortfolioHistoryPoint> history(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return dashboardService.history(user.getId(), days);
    }

    @Operation(summary = "Retorna o comportamento de mercado dos ativos em carteira: variação, faixa e preços do período")
    @ApiResponse(responseCode = "200", description = "Um item por posição aberta")
    @ApiResponse(responseCode = "400", description = "Período inválido", content = @Content)
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping("/market")
    public List<MarketAssetResponse> market(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return marketOverviewService.overview(user.getId(), days);
    }

    @Operation(
            summary = "Atualiza as cotações agora",
            description = "Atende uma coleta por minuto, para todos os usuários; fora disso, responde refreshed=false.")
    @ApiResponse(responseCode = "200", description = "Pedido processado")
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @PostMapping("/refresh-quotes")
    public QuoteRefreshResponse refreshQuotes() {
        return new QuoteRefreshResponse(quoteCollector.refresh());
    }
}
