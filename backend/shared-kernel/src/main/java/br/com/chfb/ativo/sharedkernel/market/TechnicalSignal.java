package br.com.chfb.ativo.sharedkernel.market;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Leitura técnica de um símbolo, igual para todos os usuários. Descreve o histórico de preços: não é previsão
 * nem recomendação. Com {@code available} falso, faltam preços ou eles estão antigos, e as leituras ficam nulas.
 */
public record TechnicalSignal(
        boolean available,
        Momentum momentum,
        Trend trend,
        BigDecimal rsi,
        BigDecimal shortAverage,
        BigDecimal longAverage,
        int oversoldLimit,
        int overboughtLimit,
        int closes,
        int requiredCloses,
        Instant asOf) {

    public enum Momentum {
        OVERSOLD,
        NEUTRAL,
        OVERBOUGHT
    }

    public enum Trend {
        UP,
        DOWN
    }

    public static TechnicalSignal unavailable(int oversoldLimit, int overboughtLimit, int closes, int requiredCloses, Instant asOf) {
        return new TechnicalSignal(false, null, null, null, null, null, oversoldLimit, overboughtLimit, closes, requiredCloses, asOf);
    }
}
