package br.com.chfb.ativo.predictiveengine.service;

import br.com.chfb.ativo.predictiveengine.config.PredictiveProperties;
import br.com.chfb.ativo.predictiveengine.dto.Assumptions;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetExposure;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mede retorno e volatilidade da carteira de hoje no histórico de cotações, então as compras e vendas do
 * usuário não afetam o resultado. O que não tem histórico suficiente usa as premissas de referência.
 */
final class AssumptionsEstimator {

    private static final double DAYS_PER_YEAR = 365.25;
    /** Abaixo dessa diferença, a carteira inteira foi medida: evita chamar de parcial um erro de arredondamento. */
    private static final double FULL_COVERAGE = 0.999;

    private AssumptionsEstimator() {
    }

    static Assumptions estimate(
            List<AssetExposure> exposures, Map<String, List<PricePoint>> prices, PredictiveProperties properties) {
        double total = exposures.stream().mapToDouble(exposure -> exposure.marketValue().doubleValue()).sum();

        List<MeasuredAsset> measured = new ArrayList<>();
        List<AssetExposure> notMeasured = new ArrayList<>();
        int longestHistory = 0;
        for (AssetExposure exposure : exposures) {
            TreeMap<LocalDate, Double> closes = dailyCloses(prices.getOrDefault(exposure.symbol(), List.of()));
            int days = closes.isEmpty() ? 0 : (int) ChronoUnit.DAYS.between(closes.firstKey(), closes.lastKey());
            longestHistory = Math.max(longestHistory, days);
            if (days >= properties.minHistoryDays() && exposure.marketValue().signum() > 0) {
                measured.add(new MeasuredAsset(exposure.marketValue().doubleValue(), closes));
            } else {
                notMeasured.add(exposure);
            }
        }

        double measuredValue = measured.stream().mapToDouble(MeasuredAsset::value).sum();
        if (total <= 0 || measuredValue <= 0) {
            return reference(exposures, longestHistory, properties);
        }

        LocalDate start = measured.stream().map(asset -> asset.closes().firstKey()).max(LocalDate::compareTo).orElseThrow();
        LocalDate end = measured.stream().map(asset -> asset.closes().lastKey()).max(LocalDate::compareTo).orElseThrow();
        int window = (int) ChronoUnit.DAYS.between(start, end);
        // Ativos com históricos que quase não se sobrepõem não formam um período comum suficiente.
        if (window < properties.minHistoryDays()) {
            return reference(exposures, longestHistory, properties);
        }

        List<Double> dailyReturns = portfolioDailyReturns(measured, measuredValue, start, end);
        double volatility = standardDeviation(dailyReturns) * Math.sqrt(DAYS_PER_YEAR);
        // Volatilidade zero indica preços parados ou faltando, e não um ativo sem risco.
        if (volatility <= 0) {
            return reference(exposures, longestHistory, properties);
        }
        double annualReturn = Math.max(
                properties.minObservedReturn(),
                Math.min(properties.maxObservedReturn(), annualTrend(dailyReturns)));

        double coverage = measuredValue / total;
        Assumptions rest = reference(notMeasured, longestHistory, properties);
        return new Assumptions(
                coverage >= FULL_COVERAGE ? Assumptions.Source.MARKET : Assumptions.Source.BLENDED,
                annualReturn * coverage + rest.annualReturn() * (1 - coverage),
                volatility * coverage + rest.annualVolatility() * (1 - coverage),
                properties.confidenceLevel(),
                window,
                coverage);
    }

    /** O último preço de cada dia. Datas em UTC, como no restante do módulo. */
    private static TreeMap<LocalDate, Double> dailyCloses(List<PricePoint> points) {
        TreeMap<LocalDate, Double> closes = new TreeMap<>();
        for (PricePoint point : points) {
            if (point.price().signum() > 0) {
                closes.put(point.time().atZone(ZoneOffset.UTC).toLocalDate(), point.price().doubleValue());
            }
        }
        return closes;
    }

    /**
     * Pesos fixos, pelo valor de hoje. Em dias sem pregão, a ação repete o último preço e o retorno é zero:
     * medir por dia corrido deixa ações e cripto, que negocia todo dia, na mesma escala.
     */
    private static List<Double> portfolioDailyReturns(
            List<MeasuredAsset> assets, double totalValue, LocalDate start, LocalDate end) {
        List<Double> returns = new ArrayList<>();
        for (LocalDate day = start.plusDays(1); !day.isAfter(end); day = day.plusDays(1)) {
            double dayReturn = 0;
            for (MeasuredAsset asset : assets) {
                double previous = asset.closes().floorEntry(day.minusDays(1)).getValue();
                double current = asset.closes().floorEntry(day).getValue();
                dayReturn += (asset.value() / totalValue) * (current / previous - 1);
            }
            returns.add(dayReturn);
        }
        return returns;
    }

    /**
     * Inclinação da reta ajustada ao logaritmo do índice da carteira, anualizada. Usa todos os dias do período:
     * o retorno composto depende só do primeiro e do último, e um pico em um deles distorce o resultado.
     */
    private static double annualTrend(List<Double> dailyReturns) {
        double logIndex = 0;
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;
        int count = dailyReturns.size() + 1;
        for (int day = 1; day < count; day++) {
            logIndex += Math.log(1 + dailyReturns.get(day - 1));
            sumX += day;
            sumY += logIndex;
            sumXY += day * logIndex;
            sumXX += (double) day * day;
        }
        double slope = (count * sumXY - sumX * sumY) / (count * sumXX - sumX * sumX);
        return Math.exp(slope * DAYS_PER_YEAR) - 1;
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
                historyDays,
                0);
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

    private record MeasuredAsset(double value, TreeMap<LocalDate, Double> closes) {
    }
}
