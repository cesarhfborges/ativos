package br.com.chfb.ativo.sharedkernel.market;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SentimentStore {

    /** Último dia calculado por símbolo; símbolos nunca calculados ficam de fora do mapa. */
    Map<String, LocalDate> findLastDays(Collection<String> symbols);

    /** Grava as notas e substitui as que já existiam para o mesmo símbolo e dia. */
    void save(List<SentimentScore> scores, Instant computedAt);

    /** Notas desde {@code from}, do dia mais antigo ao mais recente; símbolos sem notas ficam de fora do mapa. */
    Map<String, List<SentimentScore>> findSince(Collection<String> symbols, LocalDate from);
}
