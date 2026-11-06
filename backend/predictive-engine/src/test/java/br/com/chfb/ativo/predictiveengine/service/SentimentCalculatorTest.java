package br.com.chfb.ativo.predictiveengine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.SentimentScore;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SentimentCalculatorTest {

    /** Meio-dia em São Paulo. */
    private static final Instant LAST = Instant.parse("2026-11-06T15:00:00Z");
    private static final LocalDate LAST_DAY = LocalDate.of(2026, 11, 6);

    private static List<PricePoint> points(double... closes) {
        List<PricePoint> points = new ArrayList<>();
        for (int i = 0; i < closes.length; i++) {
            points.add(new PricePoint(LAST.minus(Duration.ofDays(closes.length - 1L - i)), BigDecimal.valueOf(closes[i])));
        }
        return points;
    }

    private static double[] rising(int count) {
        return IntStream.range(0, count).mapToDouble(i -> 100 * Math.pow(1.01, i)).toArray();
    }

    private static double[] falling(int count) {
        return IntStream.range(0, count).mapToDouble(i -> 100 * Math.pow(0.99, i)).toArray();
    }

    private static SentimentScore latest(double... closes) {
        List<SentimentScore> scores = SentimentCalculator.scores("X", points(closes), LAST_DAY);
        assertThat(scores).hasSize(1);
        return scores.get(0);
    }

    @Test
    void altaConstanteTendeAGanancia() {
        SentimentScore score = latest(rising(60));

        assertThat(score.rsi()).isEqualTo(100);
        assertThat(score.range()).isEqualTo(100);
        assertThat(score.trend()).isGreaterThan(90);
        assertThat(score.volatility()).isCloseTo(50, within(0.01));
        assertThat(score.score()).isGreaterThan(80);
    }

    @Test
    void quedaConstanteTendeAMedo() {
        SentimentScore score = latest(falling(60));

        assertThat(score.rsi()).isZero();
        assertThat(score.range()).isZero();
        assertThat(score.trend()).isLessThan(10);
        assertThat(score.score()).isLessThan(20);
    }

    @Test
    void oscilacaoRecenteMaiorPuxaParaOMedo() {
        // Oscila 1% e, nos últimos 20 fechamentos, 4%: a volatilidade recente é bem maior que a habitual.
        double[] closes = new double[60];
        double price = 100;
        for (int i = 0; i < closes.length; i++) {
            double step = i >= 40 ? 0.04 : 0.01;
            price *= i % 2 == 0 ? 1 + step : 1 - step;
            closes[i] = price;
        }

        assertThat(latest(closes).volatility()).isLessThan(30);
    }

    @Test
    void acaoEMoedaComAMesmaOscilacaoRelativaTemANotaDeTendenciaProxima() {
        // Mesma forma, dez vezes mais oscilação: a distância é medida na oscilação do próprio ativo.
        Random random = new Random(7);
        double[] calm = new double[60];
        double[] wild = new double[60];
        double calmPrice = 100;
        double wildPrice = 100;
        for (int i = 0; i < calm.length; i++) {
            double shock = random.nextGaussian() * 0.002 + 0.0005;
            calmPrice *= Math.exp(shock);
            wildPrice *= Math.exp(shock * 10);
            calm[i] = calmPrice;
            wild[i] = wildPrice;
        }

        assertThat(latest(calm).trend()).isCloseTo(latest(wild).trend(), within(10.0));
    }

    @Test
    void semFechamentosSuficientesNaoHaNota() {
        assertThat(SentimentCalculator.scores("X", points(rising(SentimentCalculator.REQUIRED_CLOSES - 1)), LocalDate.EPOCH)).isEmpty();
        assertThat(SentimentCalculator.scores("X", points(rising(SentimentCalculator.REQUIRED_CLOSES)), LocalDate.EPOCH)).hasSize(1);
    }

    @Test
    void fechamentosRepetidosNaoContam() {
        double[] closes = new double[120];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 100 + i / 3;
        }

        assertThat(SentimentCalculator.scores("X", points(closes), LocalDate.EPOCH)).isEmpty();
    }

    @Test
    void precoZeradoFicaDeFora() {
        List<PricePoint> points = new ArrayList<>(points(rising(60)));
        points.add(0, new PricePoint(LAST.minus(Duration.ofDays(61)), BigDecimal.ZERO));

        assertThat(SentimentCalculator.scores("X", points, LAST_DAY).get(0).score()).isBetween(0.0, 100.0);
    }

    @Test
    void comecaNoDiaPedido() {
        List<SentimentScore> scores = SentimentCalculator.scores("X", points(rising(60)), LAST_DAY.minusDays(2));

        assertThat(scores).extracting(SentimentScore::day)
                .containsExactly(LAST_DAY.minusDays(2), LAST_DAY.minusDays(1), LAST_DAY);
    }

    @Test
    void aNotaDeUmDiaNaoDependeDeQuantoHistoricoFoiLido() {
        Random random = new Random(3);
        double[] closes = new double[400];
        double price = 100;
        for (int i = 0; i < closes.length; i++) {
            price *= Math.exp(random.nextGaussian() * 0.02);
            closes[i] = price;
        }
        List<PricePoint> all = points(closes);
        // Como o refresh faz: só o último dia e a janela anterior a ele.
        List<PricePoint> recent = all.subList(all.size() - 210, all.size());

        SentimentScore full = SentimentCalculator.scores("X", all, LAST_DAY).get(0);
        SentimentScore partial = SentimentCalculator.scores("X", recent, LAST_DAY).get(0);

        assertThat(partial).isEqualTo(full);
    }

    @Test
    void oDiaSegueOFusoDeSaoPaulo() {
        // 22h de 6/11 em São Paulo já é dia 7 em UTC.
        Instant night = Instant.parse("2026-11-07T01:00:00Z");
        List<PricePoint> points = new ArrayList<>();
        double[] closes = rising(60);
        for (int i = 0; i < closes.length; i++) {
            points.add(new PricePoint(night.minus(Duration.ofDays(closes.length - 1L - i)), BigDecimal.valueOf(closes[i])));
        }

        assertThat(SentimentCalculator.scores("X", points, LAST_DAY)).extracting(SentimentScore::day).containsExactly(LAST_DAY);
    }

    @Test
    void doisPrecosNoMesmoDiaValeOUltimo() {
        List<PricePoint> points = new ArrayList<>(points(rising(60)));
        PricePoint last = points.get(points.size() - 1);
        points.add(new PricePoint(last.time().plus(Duration.ofHours(1)), BigDecimal.valueOf(1)));

        SentimentScore score = SentimentCalculator.scores("X", points, LAST_DAY).get(0);

        assertThat(score.range()).isZero();
    }
}
