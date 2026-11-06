package br.com.chfb.ativo.predictiveengine.service;

import br.com.chfb.ativo.predictiveengine.config.PredictiveProperties;
import br.com.chfb.ativo.predictiveengine.dto.Assumptions;
import br.com.chfb.ativo.predictiveengine.dto.HistoryPoint;
import br.com.chfb.ativo.predictiveengine.dto.Scenario;
import br.com.chfb.ativo.predictiveengine.dto.SimulationResponse;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetExposure;
import br.com.chfb.ativo.sharedkernel.portfolio.PortfolioData;
import br.com.chfb.ativo.sharedkernel.portfolio.PortfolioDataProvider;
import br.com.chfb.ativo.sharedkernel.portfolio.PortfolioSample;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "predictive.enabled", havingValue = "true")
public class PredictionService {

    private final PortfolioDataProvider dataProvider;
    private final PredictiveProperties properties;

    public PredictionService(PortfolioDataProvider dataProvider, PredictiveProperties properties) {
        this.dataProvider = dataProvider;
        this.properties = properties;
    }

    public SimulationResponse simulate(UUID userId, int months, BigDecimal monthlyContribution, Scenario scenario) {
        PortfolioData data = dataProvider.load(userId);
        List<PortfolioSample> daily = lastSampleOfEachDay(data.history());
        Assumptions assumptions = AssumptionsEstimator.estimate(daily, data.exposures(), properties);

        double currentValue = data.exposures().stream()
                .mapToDouble(exposure -> exposure.marketValue().doubleValue())
                .sum();
        double contribution = monthlyContribution.doubleValue();

        return new SimulationResponse(
                scenario,
                months,
                monthlyContribution,
                ProjectionCalculator.money(currentValue),
                ProjectionCalculator.money(contribution * months),
                annualReturn(assumptions, scenario),
                assumptions,
                daily.stream()
                        .map(sample -> new HistoryPoint(toDate(sample), ProjectionCalculator.money(sample.total().doubleValue())))
                        .toList(),
                ProjectionCalculator.project(
                        LocalDate.now(ZoneOffset.UTC),
                        startingValue(data.exposures(), currentValue, scenario),
                        contribution,
                        months,
                        annualReturn(assumptions, scenario),
                        assumptions.annualVolatility(),
                        properties.confidenceZ()),
                RiskAnalyzer.analyze(data.exposures(), properties.risk()));
    }

    private double annualReturn(Assumptions assumptions, Scenario scenario) {
        return switch (scenario) {
            case OPTIMISTIC -> assumptions.annualReturn() + properties.scenarios().optimisticReturnBonus();
            case REALISTIC -> assumptions.annualReturn();
            case PESSIMISTIC -> assumptions.annualReturn() - properties.scenarios().pessimisticReturnPenalty();
        };
    }

    /** O cenário pessimista começa com uma queda imediata na classe de maior risco presente na carteira. */
    private double startingValue(List<AssetExposure> exposures, double currentValue, Scenario scenario) {
        if (scenario != Scenario.PESSIMISTIC) {
            return currentValue;
        }
        double crypto = AssumptionsEstimator.sum(exposures, AssetClass.CRYPTO);
        return crypto > 0
                ? currentValue - crypto * properties.scenarios().cryptoStress()
                : currentValue - AssumptionsEstimator.sum(exposures, AssetClass.STOCK) * properties.scenarios().stockStress();
    }

    /** Pontos horários têm ruído demais para medir tendência e deixariam a resposta muito grande. */
    private static List<PortfolioSample> lastSampleOfEachDay(List<PortfolioSample> history) {
        Map<LocalDate, PortfolioSample> byDay = new TreeMap<>();
        history.forEach(sample -> byDay.put(toDate(sample), sample));
        return new ArrayList<>(byDay.values());
    }

    private static LocalDate toDate(PortfolioSample sample) {
        return sample.time().atZone(ZoneOffset.UTC).toLocalDate();
    }
}
