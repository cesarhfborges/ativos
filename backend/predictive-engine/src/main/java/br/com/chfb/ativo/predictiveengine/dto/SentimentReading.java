package br.com.chfb.ativo.predictiveengine.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Notas de 0 (medo) a 100 (ganância); {@code coverage}, de 0 a 1, é a parte da carteira que tem nota nesse dia. */
public record SentimentReading(
        LocalDate day,
        BigDecimal score,
        BigDecimal rsi,
        BigDecimal trend,
        BigDecimal range,
        BigDecimal volatility,
        BigDecimal coverage) {
}
