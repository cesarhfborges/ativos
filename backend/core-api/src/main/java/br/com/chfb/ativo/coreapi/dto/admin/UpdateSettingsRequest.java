package br.com.chfb.ativo.coreapi.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

/** Só as chaves alteradas. Tudo é salvo junto: se uma for inválida, nenhuma muda. */
public record UpdateSettingsRequest(@NotEmpty Map<String, String> values) {
}
