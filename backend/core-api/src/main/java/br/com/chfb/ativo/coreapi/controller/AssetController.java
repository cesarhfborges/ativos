package br.com.chfb.ativo.coreapi.controller;

import br.com.chfb.ativo.assetmanagement.dto.AssetResponse;
import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import br.com.chfb.ativo.assetmanagement.service.WalletService;
import br.com.chfb.ativo.coreapi.config.OpenApiConfig;
import br.com.chfb.ativo.sharedkernel.market.AssetSearch;
import br.com.chfb.ativo.sharedkernel.market.AssetSearchResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
@Tag(name = OpenApiConfig.ASSET_TAG)
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class AssetController {

    private final WalletService walletService;
    private final AssetSearch assetSearch;

    public AssetController(WalletService walletService, AssetSearch assetSearch) {
        this.walletService = walletService;
        this.assetSearch = assetSearch;
    }

    @Operation(
            summary = "Busca ativos já cadastrados no sistema",
            description = "Procura no início do símbolo ou em qualquer parte do nome. Devolve no máximo 10 ativos.")
    @ApiResponse(responseCode = "200", description = "Ativos encontrados")
    @ApiResponse(responseCode = "400", description = "Busca inválida", content = @Content)
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping
    public List<AssetResponse> search(@RequestParam @NotBlank @Size(max = 120) String query) {
        return walletService.searchAssets(query);
    }

    @Operation(
            summary = "Busca ativos no provedor de cotações",
            description = "Inclui ativos ainda não cadastrados, com nome e cotação atual. Devolve no máximo 10 ativos. "
                    + "Com available falso, o provedor não respondeu: isso não significa que o ativo não existe.")
    @ApiResponse(responseCode = "200", description = "Resultado da busca")
    @ApiResponse(responseCode = "400", description = "Busca inválida", content = @Content)
    @ApiResponse(responseCode = "401", description = "Sessão ausente ou expirada", content = @Content)
    @GetMapping("/lookup")
    public AssetSearchResult lookup(@RequestParam @NotBlank @Size(max = 120) String query, @RequestParam AssetType type) {
        return assetSearch.search(query, type.toAssetClass());
    }
}
