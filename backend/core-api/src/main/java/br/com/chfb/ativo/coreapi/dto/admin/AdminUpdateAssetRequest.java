package br.com.chfb.ativo.coreapi.dto.admin;

import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUpdateAssetRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @NotNull
        AssetType assetType) {
}
