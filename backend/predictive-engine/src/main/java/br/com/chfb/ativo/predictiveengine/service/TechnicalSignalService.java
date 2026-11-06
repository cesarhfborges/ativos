package br.com.chfb.ativo.predictiveengine.service;

import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.PriceResolution;
import br.com.chfb.ativo.sharedkernel.market.QuoteLookup;
import br.com.chfb.ativo.sharedkernel.market.TechnicalSignal;
import br.com.chfb.ativo.sharedkernel.market.TechnicalSignals;
import br.com.chfb.ativo.sharedkernel.settings.AppSettings;
import br.com.chfb.ativo.sharedkernel.settings.SettingKeys;
import br.com.chfb.ativo.sharedkernel.settings.SettingValues;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** O mesmo cálculo para todos os usuários, por símbolo, a partir dos fechamentos diários já gravados. */
@Service
@ConditionalOnProperty(name = "predictive.enabled", havingValue = "true")
public class TechnicalSignalService implements TechnicalSignals {

    static final int RSI_PERIOD = 14;
    static final int SHORT_AVERAGE = 20;
    static final int LONG_AVERAGE = 50;
    /** A média longa é o que exige mais dados. Cabe nos 3 meses de histórico do plano gratuito da Brapi. */
    private static final int REQUIRED_CLOSES = LONG_AVERAGE;
    /** Mais que o necessário em dias corridos, porque nem todo dia tem pregão; o excedente estabiliza o RSI. */
    private static final Duration LOOKBACK = Duration.ofDays(200);
    /** Mais que um feriado prolongado: passou disso, a coleta parou e a leitura descreveria o passado. */
    private static final Duration MAX_AGE = Duration.ofDays(5);
    private static final int RSI_SCALE = 1;
    private static final int PRICE_SCALE = 8;

    private final QuoteLookup quoteLookup;
    private final AppSettings appSettings;

    public TechnicalSignalService(QuoteLookup quoteLookup, AppSettings appSettings) {
        this.quoteLookup = quoteLookup;
        this.appSettings = appSettings;
    }

    @Override
    public Map<String, TechnicalSignal> forSymbols(Collection<String> symbols) {
        Instant now = Instant.now();
        SettingValues settings = appSettings.current();
        int oversold = settings.getInt(SettingKeys.SIGNAL_OVERSOLD);
        int overbought = settings.getInt(SettingKeys.SIGNAL_OVERBOUGHT);
        Map<String, List<PricePoint>> history = quoteLookup.findPriceHistory(symbols, now.minus(LOOKBACK), PriceResolution.DAY);
        Map<String, TechnicalSignal> signals = new LinkedHashMap<>();
        for (String symbol : symbols) {
            signals.put(symbol, evaluate(history.getOrDefault(symbol, List.of()), oversold, overbought, now));
        }
        return signals;
    }

    static TechnicalSignal evaluate(List<PricePoint> dailyPoints, int oversold, int overbought, Instant now) {
        List<Double> closes = TechnicalIndicators.tradedCloses(dailyPoints);
        Instant asOf = dailyPoints.isEmpty() ? null : dailyPoints.get(dailyPoints.size() - 1).time();
        if (closes.size() < REQUIRED_CLOSES || asOf.isBefore(now.minus(MAX_AGE))) {
            return TechnicalSignal.unavailable(oversold, overbought, closes.size(), REQUIRED_CLOSES, asOf);
        }
        double rsi = TechnicalIndicators.rsi(closes, RSI_PERIOD);
        double shortAverage = TechnicalIndicators.simpleAverage(closes, SHORT_AVERAGE);
        double longAverage = TechnicalIndicators.simpleAverage(closes, LONG_AVERAGE);
        return new TechnicalSignal(
                true,
                rsi <= oversold
                        ? TechnicalSignal.Momentum.OVERSOLD
                        : rsi >= overbought ? TechnicalSignal.Momentum.OVERBOUGHT : TechnicalSignal.Momentum.NEUTRAL,
                shortAverage >= longAverage ? TechnicalSignal.Trend.UP : TechnicalSignal.Trend.DOWN,
                scaled(rsi, RSI_SCALE),
                scaled(shortAverage, PRICE_SCALE),
                scaled(longAverage, PRICE_SCALE),
                oversold,
                overbought,
                closes.size(),
                REQUIRED_CLOSES,
                asOf);
    }

    private static BigDecimal scaled(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
