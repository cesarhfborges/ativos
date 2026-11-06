package br.com.chfb.ativo.predictiveengine.dto;

/** Premissas usadas no cálculo, sempre devolvidas para o usuário saber de onde veio a projeção. */
public record Assumptions(Source source, double annualReturn, double annualVolatility, double confidenceLevel, int historyDays) {

    public enum Source {
        /** Medido nos snapshots da própria carteira. */
        OBSERVED,
        /** Valores configurados, por falta de histórico suficiente. */
        REFERENCE
    }
}
