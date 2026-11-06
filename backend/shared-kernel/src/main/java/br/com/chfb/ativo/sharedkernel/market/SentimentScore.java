package br.com.chfb.ativo.sharedkernel.market;

import java.time.LocalDate;

/** Notas de 0 (medo) a 100 (ganância) de um símbolo em um dia, igual para todos os usuários. */
public record SentimentScore(
        String symbol,
        LocalDate day,
        double score,
        double rsi,
        double trend,
        double range,
        double volatility) {
}
