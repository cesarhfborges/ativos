package br.com.chfb.ativo.predictiveengine.service;

import br.com.chfb.ativo.sharedkernel.market.ActiveSymbols;
import br.com.chfb.ativo.sharedkernel.market.ActiveSymbolsProvider;
import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.PriceResolution;
import br.com.chfb.ativo.sharedkernel.market.QuoteLookup;
import br.com.chfb.ativo.sharedkernel.market.QuotesCollectedEvent;
import br.com.chfb.ativo.sharedkernel.market.SentimentScore;
import br.com.chfb.ativo.sharedkernel.market.SentimentStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Atualiza as notas ao fim de cada coleta agendada, usando só os preços gravados. Refaz o último dia calculado, que
 * pode ter mudado, e todos os seguintes; quem ainda não tem nota começa do início do histórico. Assim, uma carga de
 * fechamentos que chegou depois ou um período sem coleta se resolvem sozinhos na próxima execução.
 */
@Component
@ConditionalOnProperty(name = "predictive.enabled", havingValue = "true")
public class SentimentRefresher {

    private static final Logger log = LoggerFactory.getLogger(SentimentRefresher.class);

    private final ActiveSymbolsProvider activeSymbolsProvider;
    private final QuoteLookup quoteLookup;
    private final SentimentStore store;

    public SentimentRefresher(ActiveSymbolsProvider activeSymbolsProvider, QuoteLookup quoteLookup, SentimentStore store) {
        this.activeSymbolsProvider = activeSymbolsProvider;
        this.quoteLookup = quoteLookup;
        this.store = store;
    }

    /** O evento é síncrono: uma exceção aqui impediria os snapshots patrimoniais e a limpeza do registro de coletas. */
    @EventListener
    public void onQuotesCollected(QuotesCollectedEvent event) {
        try {
            int saved = refresh(event.collectedAt());
            log.info("Índice de ganância e medo: {} notas atualizadas", saved);
        } catch (RuntimeException e) {
            log.warn("Falha ao atualizar o índice de ganância e medo; será refeito na próxima coleta", e);
        }
    }

    int refresh(Instant now) {
        ActiveSymbols active = activeSymbolsProvider.activeSymbols();
        Set<String> symbols = new HashSet<>(active.crypto());
        symbols.addAll(active.stocks());
        if (symbols.isEmpty()) {
            return 0;
        }
        Map<String, LocalDate> lastDays = store.findLastDays(symbols);

        Set<String> calculated = new HashSet<>(lastDays.keySet());
        Set<String> uncalculated = new HashSet<>(symbols);
        uncalculated.removeAll(calculated);

        List<SentimentScore> scores = new ArrayList<>();
        if (!calculated.isEmpty()) {
            LocalDate oldest = lastDays.values().stream().min(Comparator.naturalOrder()).orElseThrow();
            Instant from = oldest.atStartOfDay(SentimentCalculator.ZONE).toInstant().minus(SentimentCalculator.LOOKBACK);
            Map<String, List<PricePoint>> history = quoteLookup.findPriceHistory(calculated, from, PriceResolution.DAY);
            calculated.forEach(symbol -> scores.addAll(
                    SentimentCalculator.scores(symbol, history.getOrDefault(symbol, List.of()), lastDays.get(symbol))));
        }
        if (!uncalculated.isEmpty()) {
            Map<String, List<PricePoint>> history = quoteLookup.findPriceHistory(uncalculated, Instant.EPOCH, PriceResolution.DAY);
            uncalculated.forEach(symbol -> scores.addAll(
                    SentimentCalculator.scores(symbol, history.getOrDefault(symbol, List.of()), LocalDate.EPOCH)));
        }
        if (!scores.isEmpty()) {
            store.save(scores, now);
        }
        return scores.size();
    }
}
