package br.com.chfb.ativo.predictiveengine.service;

import br.com.chfb.ativo.predictiveengine.config.PredictiveProperties;
import br.com.chfb.ativo.predictiveengine.dto.Assumptions;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetExposure;
import br.com.chfb.ativo.sharedkernel.portfolio.PortfolioSample;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Mede retorno e volatilidade na carteira ou, com pouco histórico, usa as premissas de referência. */
final class AssumptionsEstimator {

    private static final double DAYS_PER_YEAR = 365.25;

    private AssumptionsEstimator() {
    }

    /**
     * Recebe um ponto por dia. O retorno de cada período desconta a variação do custo, para que uma compra
     * não pareça valorização: {@code (Δ valor de mercado - Δ custo) / valor de mercado anterior}.
     */
    static Assumptions estimate(List<PortfolioSample> daily, List<AssetExposure> exposures, PredictiveProperties properties) {
        int historyDays = daily.size() < 2
                ? 0
                : (int) Duration.between(daily.get(0).time(), daily.get(daily.size() - 1).time()).toDays();

        if (historyDays >= properties.minHistoryDays()) {
            double growth = 1;
            List<Double> dailyReturns = new ArrayList<>();
            for (int i = 1; i < daily.size(); i++) {
                PortfolioSample previous = daily.get(i - 1);
                PortfolioSample current = daily.get(i);
                double previousTotal = previous.total().doubleValue();
                if (previousTotal <= 0) {
                    continue;
                }
                double marketChange = current.total().subtract(previous.total()).doubleValue();
                double costChange = current.invested().subtract(previous.invested()).doubleValue();
                double periodReturn = (marketChange - costChange) / previousTotal;
                double days = Math.max(Duration.between(previous.time(), current.time()).toHours() / 24.0, 1);
                growth *= 1 + periodReturn;
                dailyReturns.add(periodReturn / Math.sqrt(days));
            }
            double volatility = standardDeviation(dailyReturns) * Math.sqrt(DAYS_PER_YEAR);
            // Volatilidade zero indica falta de cotações, e não um ativo sem risco.
            if (growth > 0 && volatility > 0) {
                double annualReturn = Math.pow(growth, DAYS_PER_YEAR / historyDays) - 1;
                double capped = Math.max(properties.minObservedReturn(), Math.min(properties.maxObservedReturn(), annualReturn));
                return new Assumptions(Assumptions.Source.OBSERVED, capped, volatility, properties.confidenceLevel(), historyDays);
            }
        }
        return reference(exposures, historyDays, properties);
    }

    /** Média ponderada pela carteira. Somar as volatilidades é conservador: ignora a diversificação. */
    private static Assumptions reference(List<AssetExposure> exposures, int historyDays, PredictiveProperties properties) {
        double crypto = sum(exposures, AssetClass.CRYPTO);
        double total = crypto + sum(exposures, AssetClass.STOCK);
        double cryptoShare = total > 0 ? crypto / total : 0;
        PredictiveProperties.Reference reference = properties.reference();
        return new Assumptions(
                Assumptions.Source.REFERENCE,
                reference.stockReturn() * (1 - cryptoShare) + reference.cryptoReturn() * cryptoShare,
                reference.stockVolatility() * (1 - cryptoShare) + reference.cryptoVolatility() * cryptoShare,
                properties.confidenceLevel(),
                historyDays);
    }

    static double sum(List<AssetExposure> exposures, AssetClass assetClass) {
        return exposures.stream()
                .filter(exposure -> exposure.assetClass() == assetClass)
                .mapToDouble(exposure -> exposure.marketValue().doubleValue())
                .sum();
    }

    private static double standardDeviation(List<Double> values) {
        if (values.size() < 2) {
            return 0;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double squares = values.stream().mapToDouble(value -> (value - mean) * (value - mean)).sum();
        return Math.sqrt(squares / (values.size() - 1));
    }
}
