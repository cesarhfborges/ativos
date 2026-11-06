package br.com.chfb.ativo.coreapi.dto.admin;

import java.math.BigDecimal;

/** Valores em texto, como guardados. Percentuais em fração: 0.05 é 5%. {@code min} e {@code max} são nulos em chaves booleanas. */
public record SettingResponse(
        String key,
        String group,
        String label,
        String description,
        String type,
        BigDecimal min,
        BigDecimal max,
        String value,
        String defaultValue,
        boolean overridden) {
}
