package br.com.chfb.ativo.coreapi.controller.admin;

import br.com.chfb.ativo.assetmanagement.dto.AssetCatalogEntry;
import br.com.chfb.ativo.assetmanagement.dto.AssetCatalogPage;
import br.com.chfb.ativo.assetmanagement.dto.AssetVerification;
import br.com.chfb.ativo.assetmanagement.service.AssetCatalogService;
import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.coreapi.dto.admin.AdminUpdateAssetRequest;
import br.com.chfb.ativo.coreapi.security.AuthenticatedUser;
import br.com.chfb.ativo.coreapi.service.admin.AdminOperationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/admin/assets")
@Tag(name = OpenApiConfig.ADMIN_TAG)
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class AdminAssetController {

    private final AssetCatalogService catalog;
    private final AdminOperationsService operations;

    public AdminAssetController(AssetCatalogService catalog, AdminOperationsService operations) {
        this.catalog = catalog;
        this.operations = operations;
    }

    @Operation(summary = "Lista o catálogo de ativos, com o número de posições e operações de cada um")
    @GetMapping
    public AssetCatalogPage list(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalog.list(query, page, size);
    }

    @Operation(summary = "Corrige nome e tipo do ativo", description = "Vale para todos os usuários.")
    @PutMapping("/{id}")
    public AssetCatalogEntry update(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateAssetRequest body,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
        return operations.updateAsset(id, body.name(), body.assetType(), actor.getUsername());
    }

    @Operation(summary = "Confere o ativo no provedor de cotações")
    @PostMapping("/{id}/verify")
    public AssetVerification verify(
            @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
        return operations.verifyAsset(id, actor.getUsername());
    }

    @Operation(summary = "Remove um ativo sem nenhuma operação registrada")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser actor) {
        operations.removeAsset(id, actor.getUsername());
    }
}
