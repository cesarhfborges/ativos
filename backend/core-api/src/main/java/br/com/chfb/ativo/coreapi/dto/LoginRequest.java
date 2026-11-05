package br.com.chfb.ativo.coreapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "ana@example.com") @NotBlank @Size(max = 254) String email,
        @Schema(example = "senha-forte-123") @NotBlank @Size(max = 72) String password) {
}
