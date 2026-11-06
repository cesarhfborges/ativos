package br.com.chfb.ativo.sharedkernel.market;

import java.util.Collection;
import java.util.Map;

/** Só existe com o módulo de projeções ligado. Calcula a partir do histórico gravado, sem chamar os provedores. */
public interface TechnicalSignals {

    /** Todos os símbolos pedidos aparecem no mapa, mesmo sem preços. */
    Map<String, TechnicalSignal> forSymbols(Collection<String> symbols);
}
