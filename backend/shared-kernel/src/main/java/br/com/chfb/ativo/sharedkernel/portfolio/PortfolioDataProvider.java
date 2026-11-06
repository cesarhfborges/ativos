package br.com.chfb.ativo.sharedkernel.portfolio;

import java.util.List;
import java.util.UUID;

/** Dá acesso à carteira sem expor os detalhes de compra e venda aos módulos de análise. */
public interface PortfolioDataProvider {

    PortfolioData load(UUID userId);

    /** Só a exposição atual, para quem não precisa do histórico patrimonial. */
    List<AssetExposure> exposures(UUID userId);
}
