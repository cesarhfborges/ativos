package br.com.chfb.ativo.coreapi.controller;

import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.markettracker.dto.SyncStatusResponse;
import br.com.chfb.ativo.markettracker.service.SyncLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
@Tag(name = OpenApiConfig.DASHBOARD_TAG)
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class MarketController {

    private final SyncLog syncLog;

    public MarketController(SyncLog syncLog) {
        this.syncLog = syncLog;
    }

    @Operation(summary = "Informa a última e a próxima sincronização das cotações", description = "Lê do banco, sem chamar os provedores.")
    @ApiResponse(responseCode = "200", description = "Situação da sincronização")
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping("/sync-status")
    public SyncStatusResponse syncStatus() {
        return syncLog.status();
    }
}
