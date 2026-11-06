package br.com.chfb.ativo.sharedkernel.market;

import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;

/** Publicado quando um símbolo é comprado ou acompanhado, para ele não esperar a próxima coleta geral sem cotação. */
public record SymbolTrackedEvent(String symbol, AssetClass assetClass) {
}
