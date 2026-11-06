package br.com.chfb.ativo.predictiveengine.dto;

/**
 * Premissas usadas no cálculo, sempre devolvidas para o usuário saber de onde veio a projeção.
 * {@code coverage} é a fração da carteira, de 0 a 1, medida nas cotações; {@code historyDays}, os dias medidos.
 */
public record Assumptions(
        Source source, double annualReturn, double annualVolatility, double confidenceLevel, int historyDays, double coverage) {

    public enum Source {
        /** Toda a carteira foi medida no histórico de cotações. */
        MARKET,
        /** Parte medida nas cotações e parte nos valores configurados. */
        BLENDED,
        /** Só valores configurados, por falta de histórico suficiente. */
        REFERENCE
    }
}
