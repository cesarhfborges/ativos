package br.com.chfb.ativo.predictiveengine.dto;

/** {@code suggestion} traz a sugestão de rebalanceamento, quando houver. */
public record RiskAlert(Severity severity, String title, String message, String suggestion) {

    public enum Severity {
        INFO,
        WARNING
    }
}
