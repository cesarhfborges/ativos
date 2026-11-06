package br.com.chfb.ativo.predictiveengine.service;

import br.com.chfb.ativo.predictiveengine.dto.SentimentPoint;
import br.com.chfb.ativo.predictiveengine.dto.SentimentReading;
import br.com.chfb.ativo.predictiveengine.dto.SentimentResponse;
import br.com.chfb.ativo.sharedkernel.market.SentimentScore;
import br.com.chfb.ativo.sharedkernel.market.SentimentStore;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetExposure;
import br.com.chfb.ativo.sharedkernel.portfolio.PortfolioDataProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Combina as notas por símbolo com o valor atual de cada posição. Os pesos ficam fora do que é gravado: compras,
 * vendas, estornos e lançamentos retroativos valem na leitura seguinte, sem nada para corrigir no passado.
 */
@Service
@ConditionalOnProperty(name = "predictive.enabled", havingValue = "true")
public class PortfolioSentimentService {

    /** Ação não tem preço em fim de semana e feriado. Sem repetir a última nota, a curva saltaria nesses dias. */
    private static final int MAX_CARRY_DAYS = 5;
    /** Abaixo disso, o ponto falaria de poucos ativos como se fosse a carteira inteira. */
    private static final double MIN_COVERAGE = 0.5;
    private static final int SCORE_SCALE = 1;
    private static final int COVERAGE_SCALE = 4;

    private final PortfolioDataProvider dataProvider;
    private final SentimentStore store;

    public PortfolioSentimentService(PortfolioDataProvider dataProvider, SentimentStore store) {
        this.dataProvider = dataProvider;
        this.store = store;
    }

    public SentimentResponse sentiment(UUID userId, int days) {
        LocalDate today = LocalDate.now(SentimentCalculator.ZONE);
        LocalDate from = today.minusDays(days);
        List<AssetExposure> exposures = dataProvider.exposures(userId);
        List<String> symbols = exposures.stream().map(AssetExposure::symbol).toList();
        return combine(exposures, store.findSince(symbols, from.minusDays(MAX_CARRY_DAYS)), from, today);
    }

    static SentimentResponse combine(
            List<AssetExposure> exposures, Map<String, List<SentimentScore>> scores, LocalDate from, LocalDate today) {
        Map<String, NavigableMap<LocalDate, SentimentScore>> bySymbol = scores.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    NavigableMap<LocalDate, SentimentScore> byDay = new TreeMap<>();
                    entry.getValue().forEach(score -> byDay.put(score.day(), score));
                    return byDay;
                }));
        // Até a última nota real: repetir além dela desenharia uma linha reta sobre uma coleta parada.
        LocalDate lastDay = bySymbol.values().stream()
                .map(NavigableMap::lastKey)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (lastDay == null) {
            return new SentimentResponse(null, List.of(), false);
        }

        List<SentimentPoint> points = new ArrayList<>();
        SentimentReading current = null;
        for (LocalDate day = from; !day.isAfter(lastDay); day = day.plusDays(1)) {
            SentimentReading reading = reading(exposures, bySymbol, day);
            if (reading != null) {
                points.add(new SentimentPoint(day, reading.score()));
                current = reading;
            }
        }
        return new SentimentResponse(current, points, lastDay.isBefore(today.minusDays(MAX_CARRY_DAYS)));
    }

    /** Nulo sem cobertura suficiente. Ativos sem nota saem da média, e os demais dividem o peso. */
    private static SentimentReading reading(
            List<AssetExposure> exposures, Map<String, NavigableMap<LocalDate, SentimentScore>> bySymbol, LocalDate day) {
        double total = 0;
        double covered = 0;
        List<Weighted> known = new ArrayList<>();
        for (AssetExposure exposure : exposures) {
            double weight = exposure.marketValue().doubleValue();
            if (weight <= 0) {
                continue;
            }
            total += weight;
            NavigableMap<LocalDate, SentimentScore> byDay = bySymbol.get(exposure.symbol());
            Map.Entry<LocalDate, SentimentScore> latest = byDay == null ? null : byDay.floorEntry(day);
            if (latest != null && !latest.getKey().isBefore(day.minusDays(MAX_CARRY_DAYS))) {
                covered += weight;
                known.add(new Weighted(weight, latest.getValue()));
            }
        }
        if (total <= 0 || covered / total < MIN_COVERAGE) {
            return null;
        }
        return new SentimentReading(
                day,
                average(known, covered, SentimentScore::score),
                average(known, covered, SentimentScore::rsi),
                average(known, covered, SentimentScore::trend),
                average(known, covered, SentimentScore::range),
                average(known, covered, SentimentScore::volatility),
                BigDecimal.valueOf(covered / total).setScale(COVERAGE_SCALE, RoundingMode.HALF_UP));
    }

    private static BigDecimal average(List<Weighted> known, double totalWeight, ToDoubleFunction<SentimentScore> field) {
        double sum = known.stream().mapToDouble(item -> item.weight() * field.applyAsDouble(item.score())).sum();
        return BigDecimal.valueOf(sum / totalWeight).setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }

    private record Weighted(double weight, SentimentScore score) {
    }
}
