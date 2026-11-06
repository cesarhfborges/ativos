package br.com.chfb.ativo.predictiveengine.dto;

import java.util.List;

/**
 * Índice da carteira com os pesos de hoje. Com {@code current} nulo, não há dados suficientes. Com {@code stale}
 * verdadeiro, a nota mais recente tem mais de 5 dias, como quando a coleta está pausada.
 */
public record SentimentResponse(SentimentReading current, List<SentimentPoint> points, boolean stale) {
}
