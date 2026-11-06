package br.com.chfb.ativo.predictiveengine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.TechnicalSignal;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TechnicalSignalServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    /** Um fechamento por dia, terminando hoje. */
    private static List<PricePoint> points(double... closes) {
        List<PricePoint> points = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            points.add(new PricePoint(NOW.minus(Duration.ofDays(closes.length - 1L - i)), BigDecimal.valueOf(closes[i])));
        }
        return points;
    }

    private static double[] rising(int count) {
        return IntStream.range(0, count).mapToDouble(i -> 100 + i).toArray();
    }

    private static double[] falling(int count) {
        return IntStream.range(0, count).mapToDouble(i -> 500 - i).toArray();
    }

    private static TechnicalSignal evaluate(double... closes) {
        return TechnicalSignalService.evaluate(points(closes), 30, 70, NOW);
    }

    @Test
    void rsiConfereComOExemploClassicoDeWilder() {
        // Série de referência da literatura: o primeiro RSI de 14 períodos é cerca de 70,53.
        List<Double> closes = DoubleStream.of(
                        44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08, 45.89, 46.03, 45.61, 46.28, 46.28)
                .boxed()
                .toList();

        assertThat(TechnicalIndicators.rsi(closes, 14)).isCloseTo(70.53, within(0.1));
    }

    @Test
    void rsiSuavizaOsFechamentosSeguintes() {
        // Continuação da mesma série: o fechamento seguinte de 46,00 leva a cerca de 66,32.
        List<Double> closes = DoubleStream.of(
                        44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42, 45.84, 46.08, 45.89, 46.03, 45.61, 46.28, 46.28, 46.00)
                .boxed()
                .toList();

        assertThat(TechnicalIndicators.rsi(closes, 14)).isCloseTo(66.32, within(0.5));
    }

    @Test
    void mediaUsaOsUltimosFechamentos() {
        assertThat(TechnicalIndicators.simpleAverage(List.of(1d, 2d, 3d, 4d), 2)).isEqualTo(3.5);
    }

    @Test
    void soAltasSaoSobrecompradoEmTendenciaDeAlta() {
        TechnicalSignal signal = evaluate(rising(60));

        assertThat(signal.available()).isTrue();
        assertThat(signal.momentum()).isEqualTo(TechnicalSignal.Momentum.OVERBOUGHT);
        assertThat(signal.trend()).isEqualTo(TechnicalSignal.Trend.UP);
        assertThat(signal.rsi()).isEqualByComparingTo("100");
    }

    @Test
    void soQuedasSaoSobrevendidoEmTendenciaDeBaixa() {
        TechnicalSignal signal = evaluate(falling(60));

        assertThat(signal.momentum()).isEqualTo(TechnicalSignal.Momentum.OVERSOLD);
        assertThat(signal.trend()).isEqualTo(TechnicalSignal.Trend.DOWN);
        assertThat(signal.rsi()).isEqualByComparingTo("0");
    }

    @Test
    void osLimitesConfiguradosDecidemALeitura() {
        double[] alternating = IntStream.range(0, 60).mapToDouble(i -> i % 2 == 0 ? 100 : 101).toArray();

        assertThat(TechnicalSignalService.evaluate(points(alternating), 30, 70, NOW).momentum()).isEqualTo(TechnicalSignal.Momentum.NEUTRAL);
        assertThat(TechnicalSignalService.evaluate(points(alternating), 45, 55, NOW).momentum()).isEqualTo(TechnicalSignal.Momentum.NEUTRAL);
        assertThat(TechnicalSignalService.evaluate(points(alternating), 5, 40, NOW).momentum()).isEqualTo(TechnicalSignal.Momentum.OVERBOUGHT);
    }

    @Test
    void comPoucosFechamentosNaoHaLeitura() {
        TechnicalSignal signal = evaluate(rising(49));

        assertThat(signal.available()).isFalse();
        assertThat(signal.momentum()).isNull();
        assertThat(signal.rsi()).isNull();
        assertThat(signal.closes()).isEqualTo(49);
        assertThat(signal.requiredCloses()).isEqualTo(50);
    }

    @Test
    void cinquentaFechamentosBastam() {
        assertThat(evaluate(rising(50)).available()).isTrue();
    }

    @Test
    void semNenhumPrecoNaoHaLeitura() {
        TechnicalSignal signal = TechnicalSignalService.evaluate(List.of(), 30, 70, NOW);

        assertThat(signal.available()).isFalse();
        assertThat(signal.asOf()).isNull();
    }

    @Test
    void precosAntigosNaoGeramLeitura() {
        TechnicalSignal signal = TechnicalSignalService.evaluate(points(rising(60)), 30, 70, NOW.plus(Duration.ofDays(6)));

        assertThat(signal.available()).isFalse();
        assertThat(signal.closes()).isEqualTo(60);
    }

    @Test
    void diasSemPregaoNaoContamComoFechamentos() {
        // Dois dias "parados" a cada cinco, como um fim de semana.
        List<Double> withWeekends = new ArrayList<>();
        double[] traded = rising(50);
        for (int i = 0; i < traded.length; i++) {
            withWeekends.add(traded[i]);
            if (i % 5 == 4) {
                withWeekends.add(traded[i]);
                withWeekends.add(traded[i]);
            }
        }
        double[] closes = withWeekends.stream().mapToDouble(Double::doubleValue).toArray();

        assertThat(TechnicalIndicators.tradedCloses(points(closes))).hasSize(50);
        // Sem o filtro, os dias parados tirariam o RSI de 100.
        assertThat(evaluate(closes).rsi()).isEqualByComparingTo("100");
    }

    @Test
    void precoParadoNaoFingeUmaLeitura() {
        assertThat(evaluate(DoubleStream.generate(() -> 10).limit(60).toArray()).available()).isFalse();
    }
}
