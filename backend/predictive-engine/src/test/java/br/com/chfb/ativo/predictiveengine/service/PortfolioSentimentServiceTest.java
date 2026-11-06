package br.com.chfb.ativo.predictiveengine.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chfb.ativo.predictiveengine.dto.SentimentPoint;
import br.com.chfb.ativo.predictiveengine.dto.SentimentResponse;
import br.com.chfb.ativo.sharedkernel.market.SentimentScore;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetExposure;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortfolioSentimentServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private static AssetExposure exposure(String symbol, double value) {
        return new AssetExposure(symbol, AssetClass.CRYPTO, BigDecimal.valueOf(value));
    }

    private static SentimentScore score(String symbol, LocalDate day, double value) {
        return new SentimentScore(symbol, day, value, value, value, value, value);
    }

    private static SentimentResponse combine(List<AssetExposure> exposures, Map<String, List<SentimentScore>> scores) {
        return PortfolioSentimentService.combine(exposures, scores, TODAY.minusDays(7), TODAY);
    }

    @Test
    void ponderaPeloValorAtual() {
        SentimentResponse response = combine(
                List.of(exposure("A", 300), exposure("B", 100)),
                Map.of("A", List.of(score("A", TODAY, 80)), "B", List.of(score("B", TODAY, 40))));

        assertThat(response.current().score()).isEqualByComparingTo("70.0");
        assertThat(response.current().coverage()).isEqualByComparingTo("1");
        assertThat(response.stale()).isFalse();
    }

    @Test
    void ativoSemNotaSaiDaMediaEReduzACobertura() {
        SentimentResponse response = combine(
                List.of(exposure("A", 300), exposure("B", 100)),
                Map.of("A", List.of(score("A", TODAY, 80))));

        assertThat(response.current().score()).isEqualByComparingTo("80.0");
        assertThat(response.current().coverage()).isEqualByComparingTo("0.75");
    }

    @Test
    void comPoucaCoberturaNaoHaPonto() {
        SentimentResponse response = combine(
                List.of(exposure("A", 100), exposure("B", 300)),
                Map.of("A", List.of(score("A", TODAY, 80))));

        assertThat(response.current()).isNull();
        assertThat(response.points()).isEmpty();
    }

    @Test
    void repeteANotaDaAcaoNoFimDeSemanaParaACurvaNaoSaltar() {
        // Sexta para a ação; sábado e domingo só para a moeda.
        LocalDate friday = TODAY.minusDays(2);
        SentimentResponse response = combine(
                List.of(exposure("ACAO", 100), exposure("MOEDA", 100)),
                Map.of(
                        "ACAO", List.of(score("ACAO", friday, 20)),
                        "MOEDA", List.of(score("MOEDA", friday, 60), score("MOEDA", TODAY.minusDays(1), 60), score("MOEDA", TODAY, 60))));

        assertThat(response.points()).extracting(SentimentPoint::score)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("40"), new BigDecimal("40"), new BigDecimal("40"));
    }

    @Test
    void naoRepeteMaisQueCincoDias() {
        LocalDate old = TODAY.minusDays(6);
        SentimentResponse response = combine(
                List.of(exposure("ACAO", 300), exposure("MOEDA", 100)),
                Map.of("ACAO", List.of(score("ACAO", old, 20)), "MOEDA", List.of(score("MOEDA", TODAY, 60))));

        assertThat(response.current()).isNotNull();
        assertThat(response.current().day()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void naoDesenhaAlemDaUltimaNotaEAvisaQuandoEstaAntiga() {
        LocalDate old = TODAY.minusDays(6);
        SentimentResponse response = combine(List.of(exposure("A", 100)), Map.of("A", List.of(score("A", old, 50))));

        assertThat(response.points()).extracting(SentimentPoint::day).containsExactly(TODAY.minusDays(7 - 1));
        assertThat(response.stale()).isTrue();
    }

    @Test
    void carteiraVaziaNaoTemLeitura() {
        SentimentResponse response = combine(List.of(), Map.of());

        assertThat(response.current()).isNull();
        assertThat(response.points()).isEmpty();
        assertThat(response.stale()).isFalse();
    }
}
