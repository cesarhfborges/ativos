package br.com.chfb.ativo.predictiveengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "predictive")
public record PredictiveProperties(
        int minHistoryDays,
        double minObservedReturn,
        double maxObservedReturn,
        double confidenceLevel,
        double confidenceZ,
        Reference reference,
        Scenarios scenarios,
        Risk risk) {

    public record Reference(
            double stockReturn, double stockVolatility, double cryptoReturn, double cryptoVolatility) {
    }

    public record Scenarios(
            double optimisticReturnBonus, double pessimisticReturnPenalty, double cryptoStress, double stockStress) {
    }

    public record Risk(double maxCryptoShare, double maxAssetShare) {
    }
}
