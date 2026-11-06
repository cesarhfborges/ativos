package br.com.chfb.ativo.assetmanagement.service;

import br.com.chfb.ativo.assetmanagement.dto.DashboardOverviewResponse;
import br.com.chfb.ativo.assetmanagement.dto.DashboardPositionResponse;
import br.com.chfb.ativo.assetmanagement.dto.PortfolioHistoryPoint;
import br.com.chfb.ativo.assetmanagement.entity.Amounts;
import br.com.chfb.ativo.assetmanagement.repository.PortfolioSnapshotRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletPositionRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final WalletRepository walletRepository;
    private final WalletPositionRepository positionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final WalletTransactionRepository transactionRepository;
    private final PortfolioValuation valuation;

    public DashboardService(
            WalletRepository walletRepository,
            WalletPositionRepository positionRepository,
            PortfolioSnapshotRepository snapshotRepository,
            WalletTransactionRepository transactionRepository,
            PortfolioValuation valuation) {
        this.walletRepository = walletRepository;
        this.positionRepository = positionRepository;
        this.snapshotRepository = snapshotRepository;
        this.transactionRepository = transactionRepository;
        this.valuation = valuation;
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview(UUID userId) {
        List<DashboardPositionResponse> positions = positions(userId);

        BigDecimal invested = sum(positions, DashboardPositionResponse::investedValue);
        BigDecimal market = sum(positions, DashboardPositionResponse::marketValue);
        BigDecimal profitLoss = market.subtract(invested);
        Instant quotesUpdatedAt = positions.stream()
                .map(DashboardPositionResponse::quoteUpdatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        BigDecimal realizedProfit = walletRepository.findByUserId(userId)
                .map(wallet -> transactionRepository.sumRealizedProfit(wallet.getId()))
                .orElse(BigDecimal.ZERO);
        int withoutQuote = (int) positions.stream().filter(position -> position.currentPrice() == null).count();

        return new DashboardOverviewResponse(
                invested,
                market,
                profitLoss,
                PortfolioValuation.percent(profitLoss, invested),
                realizedProfit,
                withoutQuote,
                quotesUpdatedAt);
    }

    @Transactional(readOnly = true)
    public List<DashboardPositionResponse> positions(UUID userId) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> valuation.value(positionRepository.findActiveByWalletId(wallet.getId())))
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public List<PortfolioHistoryPoint> history(UUID userId, int days) {
        Instant from = Instant.now().minus(Duration.ofDays(days));
        return walletRepository.findByUserId(userId)
                .map(wallet -> snapshotRepository
                        .findByWalletIdAndSnapshotTimeGreaterThanEqualOrderBySnapshotTime(wallet.getId(), from)
                        .stream()
                        .map(PortfolioHistoryPoint::from)
                        .toList())
                .orElseGet(List::of);
    }

    private static BigDecimal sum(
            List<DashboardPositionResponse> positions, Function<DashboardPositionResponse, BigDecimal> field) {
        return positions.stream().map(field).reduce(Amounts.money(BigDecimal.ZERO), BigDecimal::add);
    }
}
