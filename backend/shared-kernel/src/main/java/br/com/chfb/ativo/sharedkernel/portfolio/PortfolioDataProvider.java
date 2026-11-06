package br.com.chfb.ativo.sharedkernel.portfolio;

import java.util.UUID;

/** Dá acesso à carteira sem expor os detalhes de compra e venda aos módulos de análise. */
public interface PortfolioDataProvider {

    PortfolioData load(UUID userId);
}
