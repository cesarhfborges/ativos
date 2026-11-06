package br.com.chfb.ativo.sharedkernel.portfolio;

import java.util.List;

/** Histórico em ordem cronológica e exposição atual da carteira de um usuário. */
public record PortfolioData(List<AssetExposure> exposures, List<PortfolioSample> history) {
}
