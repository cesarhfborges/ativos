package br.com.chfb.ativo.markettracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.chfb.ativo.markettracker.dto.SyncItemResponse;
import br.com.chfb.ativo.markettracker.entity.SyncItemKind;
import br.com.chfb.ativo.markettracker.entity.SyncItemStatus;
import br.com.chfb.ativo.markettracker.entity.SyncOrigin;
import br.com.chfb.ativo.markettracker.entity.SyncRunStatus;
import br.com.chfb.ativo.markettracker.provider.CryptoMarketProvider;
import br.com.chfb.ativo.markettracker.provider.PriceFetch;
import br.com.chfb.ativo.markettracker.provider.StockMarketProvider;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbols;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbolsProvider;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.Quote;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import br.com.chfb.ativo.sharedkernel.settings.AppSettings;
import br.com.chfb.ativo.sharedkernel.settings.SettingKeys;
import br.com.chfb.ativo.sharedkernel.settings.SettingValues;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class QuoteCollectorTest {

    private final ActiveSymbolsProvider active = mock(ActiveSymbolsProvider.class);
    private final CryptoMarketProvider crypto = mock(CryptoMarketProvider.class);
    private final StockMarketProvider stock = mock(StockMarketProvider.class);
    private final QuoteStore store = mock(QuoteStore.class);
    private final AppSettings appSettings = mock(AppSettings.class);
    private final SyncLog syncLog = mock(SyncLog.class);
    private final Map<String, String> settings = new HashMap<>(Map.of(
            SettingKeys.COLLECTION_ENABLED, "true", SettingKeys.MANUAL_REFRESH_SECONDS, "60"));

    private QuoteCollector collector;

    @BeforeEach
    void setUp() {
        when(appSettings.current()).thenAnswer(call -> new SettingValues(settings::get));
        when(syncLog.start(any())).thenAnswer(call -> new SyncRunRecord(call.getArgument(0)));
        when(store.symbolsWithContinuousHistory(any(), any())).thenReturn(Set.of());
        when(crypto.fetchCryptoPrices(anySet())).thenReturn(new PriceFetch(Map.of(), Map.of()));
        when(stock.fetchStockPrices(anySet())).thenReturn(new PriceFetch(Map.of(), Map.of()));
        collector = new QuoteCollector(active, Optional.of(crypto), Optional.of(stock), store, appSettings, syncLog);
    }

    @Test
    void consultaCadaSimboloUmaVezPorColeta() {
        when(active.activeSymbols()).thenReturn(new ActiveSymbols(Set.of("BTC", "XRP"), Set.of("BBSE3")));
        when(crypto.fetchCryptoPrices(Set.of("BTC", "XRP")))
                .thenReturn(new PriceFetch(Map.of("BTC", BigDecimal.ONE, "XRP", BigDecimal.ONE), Map.of()));
        when(stock.fetchStockPrices(Set.of("BBSE3")))
                .thenReturn(new PriceFetch(Map.of("BBSE3", BigDecimal.TEN), Map.of()));

        collector.collect(SyncOrigin.SCHEDULED);

        verify(crypto, times(1)).fetchCryptoPrices(anySet());
        verify(stock, times(1)).fetchStockPrices(anySet());
        SyncRunRecord run = finishedRun();
        assertThat(run.status()).isEqualTo(SyncRunStatus.SUCCESS);
        assertThat(run.requestedSymbols()).isEqualTo(3);
        assertThat(run.updatedSymbols()).isEqualTo(3);
    }

    @Test
    void falhaDeUmSimboloDeixaAColetaParcialERegistraOMotivo() {
        when(active.activeSymbols()).thenReturn(new ActiveSymbols(Set.of("BTC"), Set.of("BBSE3")));
        when(crypto.fetchCryptoPrices(Set.of("BTC"))).thenReturn(new PriceFetch(Map.of("BTC", BigDecimal.ONE), Map.of()));
        when(stock.fetchStockPrices(Set.of("BBSE3")))
                .thenReturn(new PriceFetch(Map.of(), Map.of("BBSE3", "429 Too Many Requests")));

        collector.collect(SyncOrigin.SCHEDULED);

        SyncRunRecord run = finishedRun();
        assertThat(run.status()).isEqualTo(SyncRunStatus.PARTIAL);
        assertThat(quoteItem(run, "BBSE3").status()).isEqualTo(SyncItemStatus.FAILED);
        assertThat(quoteItem(run, "BBSE3").message()).isEqualTo("429 Too Many Requests");
    }

    @Test
    void semNenhumPrecoNovoAColetaFalha() {
        when(active.activeSymbols()).thenReturn(new ActiveSymbols(Set.of(), Set.of("BBSE3")));
        when(stock.fetchStockPrices(Set.of("BBSE3"))).thenThrow(new IllegalStateException("fora do ar"));

        collector.collect(SyncOrigin.SCHEDULED);

        SyncRunRecord run = finishedRun();
        assertThat(run.status()).isEqualTo(SyncRunStatus.FAILED);
        assertThat(quoteItem(run, "BBSE3").message()).isEqualTo("fora do ar");
    }

    @Test
    void semSimbolosAColetaTemSucessoSemChamarProvedores() {
        when(active.activeSymbols()).thenReturn(new ActiveSymbols(Set.of(), Set.of()));

        collector.collect(SyncOrigin.SCHEDULED);

        assertThat(finishedRun().status()).isEqualTo(SyncRunStatus.SUCCESS);
        verifyNoInteractions(crypto, stock);
    }

    @Test
    void erroInternoFicaRegistradoEContinuaSubindo() {
        RuntimeException failure = new IllegalStateException("banco fora do ar");
        when(active.activeSymbols()).thenThrow(failure);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> collector.collect(SyncOrigin.SCHEDULED)).isSameAs(failure);

        verify(syncLog).fail(any(), org.mockito.ArgumentMatchers.eq(failure));
    }

    @Test
    void medeACoberturaDoHistoricoAntesDeGravarOsPrecos() {
        when(active.activeSymbols()).thenReturn(new ActiveSymbols(Set.of("XRP"), Set.of()));
        when(crypto.fetchCryptoPrices(Set.of("XRP"))).thenReturn(new PriceFetch(Map.of("XRP", BigDecimal.ONE), Map.of()));
        when(crypto.fetchCryptoDailyCloses("XRP")).thenReturn(List.of(new PricePoint(Instant.now(), BigDecimal.ONE)));

        collector.collect(SyncOrigin.SCHEDULED);

        InOrder order = inOrder(store);
        order.verify(store).symbolsWithContinuousHistory(any(), any());
        order.verify(store).save(any(), any());
        order.verify(store).saveHistory(org.mockito.ArgumentMatchers.eq("XRP"), any());
    }

    @Test
    void historicoContinuoNaoRecebeCarga() {
        when(active.activeSymbols()).thenReturn(new ActiveSymbols(Set.of("BTC"), Set.of()));
        when(store.symbolsWithContinuousHistory(any(), any())).thenReturn(Set.of("BTC"));

        collector.collect(SyncOrigin.SCHEDULED);

        verify(crypto, never()).fetchCryptoDailyCloses(any());
    }

    @Test
    void simboloNovoJaCobertoNaoChamaOProvedorNemRegistra() {
        when(store.findBySymbols(Set.of("BTC"))).thenReturn(Map.of("BTC", new Quote("BTC", BigDecimal.ONE, Instant.now())));
        when(store.symbolsWithContinuousHistory(any(), any())).thenReturn(Set.of("BTC"));
        when(syncLog.lastGeneralRunStartedAt()).thenReturn(Instant.now().minus(Duration.ofMinutes(5)));

        collector.collectNewSymbol("BTC", AssetClass.CRYPTO);

        verifyNoInteractions(crypto, stock);
        verify(syncLog, never()).start(any());
    }

    @Test
    void simboloNovoSemDadosBuscaSoEleUmaVez() {
        when(store.findBySymbols(Set.of("BBSE3"))).thenReturn(Map.of());
        when(stock.fetchStockPrices(Set.of("BBSE3")))
                .thenReturn(new PriceFetch(Map.of("BBSE3", BigDecimal.TEN), Map.of()));
        when(stock.fetchStockDailyCloses("BBSE3")).thenReturn(List.of(new PricePoint(Instant.now(), BigDecimal.TEN)));

        collector.collectNewSymbol("BBSE3", AssetClass.STOCK);

        verify(stock).fetchStockPrices(Set.of("BBSE3"));
        verify(stock).fetchStockDailyCloses("BBSE3");
        verifyNoInteractions(crypto, active);
        assertThat(finishedRun().origin()).isEqualTo(SyncOrigin.NEW_SYMBOL);
    }

    @Test
    void simboloQueSaiuDaColetaBuscaACotacaoDeNovo() {
        when(store.findBySymbols(Set.of("XRP")))
                .thenReturn(Map.of("XRP", new Quote("XRP", BigDecimal.ONE, Instant.now().minus(Duration.ofDays(60)))));
        when(store.symbolsWithContinuousHistory(any(), any())).thenReturn(Set.of("XRP"));
        when(syncLog.lastGeneralRunStartedAt()).thenReturn(Instant.now().minus(Duration.ofMinutes(5)));

        collector.collectNewSymbol("XRP", AssetClass.CRYPTO);

        verify(crypto).fetchCryptoPrices(Set.of("XRP"));
    }

    @Test
    void comAColetaPausadaOSimboloNovoEspera() {
        settings.put(SettingKeys.COLLECTION_ENABLED, "false");

        collector.collectNewSymbol("BBSE3", AssetClass.STOCK);

        verifyNoInteractions(crypto, stock, store);
    }

    @Test
    void coletaDoAdminRespeitaOIntervaloMinimo() {
        when(syncLog.lastGeneralRunStartedAt()).thenReturn(Instant.now().minus(Duration.ofSeconds(10)));

        assertThat(collector.refresh()).isFalse();

        verify(syncLog, never()).start(any());
    }

    @Test
    void coletaDoAdminForaDoIntervaloRodaComAOrigemCerta() {
        when(active.activeSymbols()).thenReturn(new ActiveSymbols(Set.of(), Set.of()));
        when(syncLog.lastGeneralRunStartedAt()).thenReturn(Instant.now().minus(Duration.ofMinutes(5)));

        assertThat(collector.refresh()).isTrue();

        verify(syncLog).start(SyncOrigin.ADMIN);
    }

    private SyncRunRecord finishedRun() {
        ArgumentCaptor<SyncRunRecord> run = ArgumentCaptor.forClass(SyncRunRecord.class);
        verify(syncLog).finish(run.capture());
        return run.getValue();
    }

    private static SyncItemResponse quoteItem(SyncRunRecord run, String symbol) {
        return run.items().stream()
                .filter(item -> item.kind() == SyncItemKind.QUOTE && item.symbol().equals(symbol))
                .findFirst()
                .orElseThrow();
    }
}
