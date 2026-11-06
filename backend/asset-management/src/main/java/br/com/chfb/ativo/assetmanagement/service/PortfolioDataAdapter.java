package br.com.chfb.ativo.assetmanagement.service;

import br.com.chfb.ativo.assetmanagement.dto.DashboardPositionResponse;
import br.com.chfb.ativo.assetmanagement.dto.PortfolioHistoryPoint;
import br.com.chfb.ativo.assetmanagement.entity.AssetType;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetExposure;
import br.com.chfb.ativo.sharedkernel.portfolio.PortfolioData;
import br.com.chfb.ativo.sharedkernel.portfolio.PortfolioDataProvider;
import br.com.chfb.ativo.sharedkernel.portfolio.PortfolioSample;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioDataAdapter implements PortfolioDataProvider {

    private static final int HISTORY_DAYS = 365;

    private final DashboardService dashboardService;

    public PortfolioDataAdapter(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioData load(UUID userId) {
        List<AssetExposure> exposures =
                dashboardService.positions(userId).stream().map(PortfolioDataAdapter::toExposure).toList();
        List<PortfolioSample> history =
                dashboardService.history(userId, HISTORY_DAYS).stream().map(PortfolioDataAdapter::toSample).toList();
        return new PortfolioData(exposures, history);
    }

    private static AssetExposure toExposure(DashboardPositionResponse position) {
        AssetClass assetClass = position.assetType() == AssetType.CRYPTO ? AssetClass.CRYPTO : AssetClass.STOCK;
        return new AssetExposure(position.symbol(), assetClass, position.marketValue());
    }

    private static PortfolioSample toSample(PortfolioHistoryPoint point) {
        return new PortfolioSample(point.time(), point.investedBalance(), point.totalBalance());
    }
}
