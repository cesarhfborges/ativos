package br.com.chfb.ativo.predictiveengine.service;

import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.SentimentScore;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Índice de ganância e medo de um símbolo, só com fechamentos: sem volume, opções ou redes sociais, ele descreve o
 * comportamento do preço, não o humor do mercado. Cada dia usa a mesma janela, então a nota de um dia não depende de
 * quando foi calculada.
 */
final class SentimentCalculator {

    /** O dia da B3. Em UTC, a coleta das 22h apareceria com a data de amanhã. */
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    /** A mesma janela do sinal técnico, para o RSI acompanhar o mostrado no Acompanhamento. */
    static final Duration LOOKBACK = Duration.ofDays(200);

    private static final int RSI_PERIOD = 14;
    private static final int SHORT_WINDOW = 20;
    private static final int LONG_WINDOW = 50;
    /** Uma janela longa de variações exige um fechamento a mais. */
    static final int REQUIRED_CLOSES = LONG_WINDOW + 1;
    /** Cerca de 5 semanas em desvios: a distância até a média de 50 é medida na oscilação do próprio ativo. */
    private static final double TREND_HORIZON = 5;
    private static final double TREND_LIMIT = 2;

    private SentimentCalculator() {
    }

    /** Notas de {@code firstDay} em diante, um dia por preço. Dias sem fechamentos suficientes ficam de fora. */
    static List<SentimentScore> scores(String symbol, List<PricePoint> points, LocalDate firstDay) {
        Map<LocalDate, PricePoint> lastOfDay = new TreeMap<>();
        points.stream()
                .filter(point -> point.price().signum() > 0)
                .forEach(point -> lastOfDay.merge(
                        point.time().atZone(ZONE).toLocalDate(),
                        point,
                        (kept, other) -> other.time().isAfter(kept.time()) ? other : kept));
        List<PricePoint> daily = List.copyOf(lastOfDay.values());

        List<SentimentScore> scores = new ArrayList<>();
        int windowStart = 0;
        for (int i = 0; i < daily.size(); i++) {
            Instant time = daily.get(i).time();
            LocalDate day = time.atZone(ZONE).toLocalDate();
            while (daily.get(windowStart).time().isBefore(time.minus(LOOKBACK))) {
                windowStart++;
            }
            if (!day.isBefore(firstDay)) {
                List<Double> closes = TechnicalIndicators.tradedCloses(daily.subList(windowStart, i + 1));
                if (closes.size() >= REQUIRED_CLOSES) {
                    scores.add(score(symbol, day, closes));
                }
            }
        }
        return scores;
    }

    private static SentimentScore score(String symbol, LocalDate day, List<Double> closes) {
        double rsi = TechnicalIndicators.rsi(closes, RSI_PERIOD);
        double trend = trend(closes);
        double range = range(closes);
        double volatility = volatility(closes);
        double score = Stream.of(rsi, trend, range, volatility).mapToDouble(Double::doubleValue).average().orElseThrow();
        return new SentimentScore(symbol, day, score, rsi, trend, range, volatility);
    }

    /** Distância até a média de 50, em unidades da oscilação do ativo: assim cripto e ação ficam comparáveis. */
    static double trend(List<Double> closes) {
        double close = closes.get(closes.size() - 1);
        double distance = Math.log(close / TechnicalIndicators.simpleAverage(closes, LONG_WINDOW));
        double normalized = distance / (dailyVolatility(closes, LONG_WINDOW) * TREND_HORIZON);
        return clamp((normalized + TREND_LIMIT) / (2 * TREND_LIMIT) * 100);
    }

    /** Onde o preço está entre a mínima e a máxima dos últimos 50 fechamentos. */
    static double range(List<Double> closes) {
        List<Double> window = closes.subList(closes.size() - LONG_WINDOW, closes.size());
        double min = window.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        double max = window.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        // Fechamentos seguidos são sempre diferentes, então a faixa nunca é vazia.
        return clamp((window.get(window.size() - 1) - min) / (max - min) * 100);
    }

    /** Oscilação recente sobre a habitual: o dobro é medo máximo; igual, neutro; a metade, ganância máxima. */
    static double volatility(List<Double> closes) {
        double ratio = dailyVolatility(closes, SHORT_WINDOW) / dailyVolatility(closes, LONG_WINDOW);
        return clamp(50 - 50 * Math.log(ratio) / Math.log(2));
    }

    /**
     * Raiz da média dos quadrados das variações logarítmicas. Sem subtrair a média, uma alta constante não vira
     * volatilidade zero, e fechamentos seguidos diferentes garantem que nunca é zero.
     */
    private static double dailyVolatility(List<Double> closes, int period) {
        double sum = 0;
        for (int i = closes.size() - period; i < closes.size(); i++) {
            double change = Math.log(closes.get(i) / closes.get(i - 1));
            sum += change * change;
        }
        return Math.sqrt(sum / period);
    }

    private static double clamp(double value) {
        return Math.min(100, Math.max(0, value));
    }
}
