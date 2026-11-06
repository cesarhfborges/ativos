package br.com.chfb.ativo.coreapi.controller.admin;

import br.com.chfb.ativo.assetmanagement.dto.RebuildResult;
import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.coreapi.dto.admin.AdminAuditResponse;
import br.com.chfb.ativo.coreapi.dto.admin.AdminOverviewResponse;
import br.com.chfb.ativo.coreapi.dto.admin.AdminSettingsResponse;
import br.com.chfb.ativo.coreapi.dto.admin.CollectQuotesResponse;
import br.com.chfb.ativo.coreapi.dto.admin.PageResponse;
import br.com.chfb.ativo.coreapi.dto.admin.UpdateSettingsRequest;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import br.com.chfb.ativo.coreapi.service.admin.AdminAuditService;
import br.com.chfb.ativo.coreapi.service.admin.AdminOperationsService;
import br.com.chfb.ativo.coreapi.service.admin.AppSettingsService;
import br.com.chfb.ativo.markettracker.dto.SyncItemResponse;
import br.com.chfb.ativo.markettracker.dto.SyncRunPage;
import br.com.chfb.ativo.markettracker.dto.SyncRunResponse;
import br.com.chfb.ativo.markettracker.entity.SyncOrigin;
import br.com.chfb.ativo.markettracker.entity.SyncRunStatus;
import br.com.chfb.ativo.markettracker.service.SyncLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = OpenApiConfig.ADMIN_TAG)
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class AdminSystemController {

    private final AdminOperationsService operations;
    private final AppSettingsService settings;
    private final AdminAuditService audit;
    private final SyncLog syncLog;

    public AdminSystemController(
            AdminOperationsService operations, AppSettingsService settings, AdminAuditService audit, SyncLog syncLog) {
        this.syncLog = syncLog;
        this.operations = operations;
        this.settings = settings;
        this.audit = audit;
    }

    @Operation(summary = "Retorna os totais do sistema e a saúde das cotações")
    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return operations.overview();
    }

    @Operation(summary = "Atualiza as cotações agora", description = "Respeita o intervalo mínimo; fora dele, responde refreshed=false.")
    @PostMapping("/quotes/collect")
    public CollectQuotesResponse collectQuotes(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
        return new CollectQuotesResponse(operations.collectQuotes(actor.getUsername()));
    }

    @Operation(summary = "Lista as execuções da coleta de cotações, da mais recente à mais antiga")
    @GetMapping("/sync/runs")
    public PageResponse<SyncRunResponse> syncRuns(
            @RequestParam(required = false) SyncOrigin origin,
            @RequestParam(required = false) SyncRunStatus status,
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        String term = symbol == null || symbol.isBlank() ? null : symbol.strip().toUpperCase(Locale.ROOT);
        SyncRunPage runs = syncLog.runs(origin, status, term, page, size);
        return new PageResponse<>(runs.items(), runs.total());
    }

    @Operation(summary = "Lista o resultado de cada símbolo em uma execução da coleta")
    @GetMapping("/sync/runs/{id}/items")
    public List<SyncItemResponse> syncRunItems(@PathVariable UUID id) {
        return syncLog.items(id);
    }

    @Operation(summary = "Refaz todas as posições a partir do histórico", description = "Recalcula preço médio e lucro das vendas; não reescreve snapshots.")
    @PostMapping("/positions/rebuild")
    public RebuildResult rebuildPositions(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
        return operations.rebuildPositions(actor.getUsername());
    }

    @Operation(summary = "Retorna as configurações editáveis e as informações do ambiente")
    @GetMapping("/settings")
    public AdminSettingsResponse settings() {
        return settings.describe();
    }

    @Operation(summary = "Altera configurações", description = "Tudo é salvo junto: se um valor for inválido, nenhum muda.")
    @PutMapping("/settings")
    public AdminSettingsResponse updateSettings(
            @Valid @RequestBody UpdateSettingsRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
        return settings.update(body.values(), actor.getUsername());
    }

    @Operation(summary = "Volta uma configuração ao valor padrão")
    @DeleteMapping("/settings/{key}")
    public AdminSettingsResponse restoreSetting(
            @PathVariable String key, @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
        return settings.restoreDefault(key, actor.getUsername());
    }

    @Operation(summary = "Lista as ações administrativas, da mais recente à mais antiga")
    @GetMapping("/audit")
    public PageResponse<AdminAuditResponse> auditLog(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return audit.list(page, size);
    }
}
