package br.com.chfb.ativo.predictiveengine.service;

import br.com.chfb.ativo.predictiveengine.config.PredictiveParameters;
import br.com.chfb.ativo.predictiveengine.config.PredictiveProperties;
import br.com.chfb.ativo.predictiveengine.dto.Assumptions;
import br.com.chfb.ativo.predictiveengine.dto.HistoryPoint;
import br.com.chfb.ativo.predictiveengine.dto.Scenario;
import br.com.chfb.ativo.predictiveengine.dto.SimulationResponse;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.PriceResolution;
import br.com.chfb.ativo.sharedkernel.market.QuoteLookup;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetExposure;
import br.com.chfb.ativo.sharedkernel.portfolio.PortfolioData;
import br.com.chfb.ativo.sharedkernel.portfolio.PortfolioDataProvider;
import br.com.chfb.ativo.sharedkernel.portfolio.PortfolioSample;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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

    /** Um dia além do ano: o primeiro ponto serve de base para a primeira variação. */
    private static final Duration PRICE_HISTORY = Duration.ofDays(366);

    private final PortfolioDataProvider dataProvider;
    private final QuoteLookup quoteLookup;
    private final PredictiveParameters parameters;

    public PredictionService(PortfolioDataProvider dataProvider, QuoteLookup quoteLookup, PredictiveParameters parameters) {
        this.dataProvider = dataProvider;
        this.quoteLookup = quoteLookup;
        this.parameters = parameters;
    }

    public SimulationResponse simulate(UUID userId, int months, BigDecimal monthlyContribution, Scenario scenario) {
        PredictiveProperties properties = parameters.current();
        PortfolioData data = dataProvider.load(userId);
        Map<String, List<PricePoint>> prices = quoteLookup.findPriceHistory(
                data.exposures().stream().map(AssetExposure::symbol).toList(),
                Instant.now().minus(PRICE_HISTORY),
                PriceResolution.DAY);
        Assumptions assumptions = AssumptionsEstimator.estimate(data.exposures(), prices, properties);

        double currentValue = data.exposures().stream()
                .mapToDouble(exposure -> exposure.marketValue().doubleValue())
                .sum();
        double contribution = monthlyContribution.doubleValue();
        double scenarioReturn = annualReturn(assumptions, scenario, properties);

        return new SimulationResponse(
                scenario,
                months,
                monthlyContribution,
                ProjectionCalculator.money(currentValue),
                ProjectionCalculator.money(contribution * months),
                scenarioReturn,
                assumptions,
                lastSampleOfEachDay(data.history()).stream()
                        .map(sample -> new HistoryPoint(toDate(sample), ProjectionCalculator.money(sample.total().doubleValue())))
                        .toList(),
                ProjectionCalculator.project(
                        LocalDate.now(ZoneOffset.UTC),
                        startingValue(data.exposures(), currentValue, scenario, properties),
                        contribution,
                        months,
                        scenarioReturn,
                        assumptions.annualVolatility(),
                        properties.confidenceZ()),
                RiskAnalyzer.analyze(data.exposures(), properties.risk()));
    }

    private static double annualReturn(Assumptions assumptions, Scenario scenario, PredictiveProperties properties) {
        return switch (scenario) {
            case OPTIMISTIC -> assumptions.annualReturn() + properties.scenarios().optimisticReturnBonus();
            case REALISTIC -> assumptions.annualReturn();
            case PESSIMISTIC -> assumptions.annualReturn() - properties.scenarios().pessimisticReturnPenalty();
        };
    }

    /** O cenário pessimista começa com uma queda imediata na classe de maior risco presente na carteira. */
    private static double startingValue(
            List<AssetExposure> exposures, double currentValue, Scenario scenario, PredictiveProperties properties) {
        if (scenario != Scenario.PESSIMISTIC) {
            return currentValue;
        }
        double crypto = AssumptionsEstimator.sum(exposures, AssetClass.CRYPTO);
        return crypto > 0
                ? currentValue - crypto * properties.scenarios().cryptoStress()
                : currentValue - AssumptionsEstimator.sum(exposures, AssetClass.STOCK) * properties.scenarios().stockStress();
    }

    /** Pontos horários deixariam a resposta muito grande para o gráfico do patrimônio real. */
    private static List<PortfolioSample> lastSampleOfEachDay(List<PortfolioSample> history) {
        Map<LocalDate, PortfolioSample> byDay = new TreeMap<>();
        history.forEach(sample -> byDay.put(toDate(sample), sample));
        return new ArrayList<>(byDay.values());
    }

    private static LocalDate toDate(PortfolioSample sample) {
        return sample.time().atZone(ZoneOffset.UTC).toLocalDate();
    }
}
