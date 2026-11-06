package br.com.chfb.ativo.coreapi.dto.admin;

import jakarta.validation.constraints.NotNull;

public record AdminSetActiveRequest(@NotNull Boolean active) {
}
