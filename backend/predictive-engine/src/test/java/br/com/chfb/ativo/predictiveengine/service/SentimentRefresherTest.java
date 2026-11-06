package br.com.chfb.ativo.predictiveengine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.chfb.ativo.sharedkernel.market.ActiveSymbols;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbolsProvider;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.PriceResolution;
import br.com.chfb.ativo.sharedkernel.market.QuoteLookup;
import br.com.chfb.ativo.sharedkernel.market.QuotesCollectedEvent;
import br.com.chfb.ativo.sharedkernel.market.SentimentScore;
import br.com.chfb.ativo.sharedkernel.market.SentimentStore;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SentimentRefresherTest {

    private static final Instant NOW = Instant.parse("2026-11-06T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private final ActiveSymbolsProvider active = mock(ActiveSymbolsProvider.class);
    private final QuoteLookup quotes = mock(QuoteLookup.class);
    private final SentimentStore store = mock(SentimentStore.class);
    private final SentimentRefresher refresher = new SentimentRefresher(active, quotes, store);

    private static List<PricePoint> history(int days) {
        List<PricePoint> points = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            points.add(new PricePoint(NOW.minus(Duration.ofDays(days - 1L - i)), BigDecimal.valueOf(100 + i)));
        }
        return points;
    }

    @SuppressWarnings("unchecked")
    private List<SentimentScore> saved() {
        ArgumentCaptor<List<SentimentScore>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).save(captor.capture(), eq(NOW));
        return captor.getValue();
    }

    @Test
    void simboloNovoCalculaTodoOHistorico() {
        when(active.activeSymbols()).thenReturn(new ActiveSymbols(Set.of("BTC"), Set.of()));
        when(store.findLastDays(any())).thenReturn(Map.of());
        when(quotes.findPriceHistory(Set.of("BTC"), Instant.EPOCH, PriceResolution.DAY)).thenReturn(Map.of("BTC", history(60)));

        assertThat(refresher.refresh(NOW)).isEqualTo(10);
        assertThat(saved()).hasSize(10);
    }

    @Test
    void simboloJaCalculadoRefazDoUltimoDiaEmDiante() {
        when(active.activeSymbols()).thenReturn(new ActiveSymbols(Set.of(), Set.of("PETR4")));
        when(store.findLastDays(any())).thenReturn(Map.of("PETR4", TODAY.minusDays(1)));
        when(quotes.findPriceHistory(eq(Set.of("PETR4")), any(), eq(PriceResolution.DAY))).thenReturn(Map.of("PETR4", history(60)));

        refresher.refresh(NOW);

        assertThat(saved()).extracting(SentimentScore::day).containsExactly(TODAY.minusDays(1), TODAY);
    }

    @Test
    void semFechamentosSuficientesNaoGravaNada() {
        when(active.activeSymbols()).thenReturn(new ActiveSymbols(Set.of("BTC"), Set.of()));
        when(store.findLastDays(any())).thenReturn(Map.of());
        when(quotes.findPriceHistory(any(), any(), any())).thenReturn(Map.of("BTC", history(10)));

        assertThat(refresher.refresh(NOW)).isZero();
        verify(store, never()).save(anyList(), any());
    }

    @Test
    void semSimbolosAtivosNaoConsultaNada() {
        when(active.activeSymbols()).thenReturn(new ActiveSymbols(Set.of(), Set.of()));

        assertThat(refresher.refresh(NOW)).isZero();
        verify(store, never()).findLastDays(any());
    }

    @Test
    void falhaNoCalculoNaoPropagaParaOsOutrosOuvintesDaColeta() {
        when(active.activeSymbols()).thenThrow(new IllegalStateException("banco fora do ar"));

        assertThatCode(() -> refresher.onQuotesCollected(new QuotesCollectedEvent(NOW))).doesNotThrowAnyException();
    }
}
