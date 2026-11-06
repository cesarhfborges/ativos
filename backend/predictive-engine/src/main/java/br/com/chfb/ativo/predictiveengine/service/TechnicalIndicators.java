package br.com.chfb.ativo.predictiveengine.service;

import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import java.util.ArrayList;
import java.util.List;

/** Cálculos puros sobre fechamentos diários, do mais antigo ao mais recente. */
final class TechnicalIndicators {

    private TechnicalIndicators() {
    }

    /**
     * A coleta grava preço todos os dias, mas ação só negocia em dia de pregão: um fim de semana viraria dias
     * "parados", que puxam o RSI para o meio e atrasam as médias. Fechamentos repetidos ficam de fora.
     */
    static List<Double> tradedCloses(List<PricePoint> dailyPoints) {
        List<Double> closes = new ArrayList<>();
        for (PricePoint point : dailyPoints) {
            double close = point.price().doubleValue();
            if (closes.isEmpty() || closes.get(closes.size() - 1) != close) {
                closes.add(close);
            }
        }
        return closes;
    }

    /** Média simples dos últimos {@code period} fechamentos. */
    static double simpleAverage(List<Double> closes, int period) {
        return closes.subList(closes.size() - period, closes.size()).stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
    }

    /**
     * RSI de Wilder, de 0 a 100: a primeira média é simples e as seguintes suavizam com o peso do período.
     * Exige {@code period + 1} fechamentos. Só com altas, é 100; sem nenhuma variação, fica no meio da escala.
     */
    static double rsi(List<Double> closes, int period) {
        double gain = 0;
        double loss = 0;
        for (int i = 1; i <= period; i++) {
            double change = closes.get(i) - closes.get(i - 1);
            gain += Math.max(change, 0);
            loss += Math.max(-change, 0);
        }
        gain /= period;
        loss /= period;
        for (int i = period + 1; i < closes.size(); i++) {
            double change = closes.get(i) - closes.get(i - 1);
            gain = (gain * (period - 1) + Math.max(change, 0)) / period;
            loss = (loss * (period - 1) + Math.max(-change, 0)) / period;
        }
        if (loss == 0) {
            return gain == 0 ? 50 : 100;
        }
        return 100 - 100 / (1 + gain / loss);
    }
}
