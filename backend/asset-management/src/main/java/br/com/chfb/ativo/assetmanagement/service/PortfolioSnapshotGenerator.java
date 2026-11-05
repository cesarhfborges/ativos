package br.com.chfb.ativo.assetmanagement.service;

import br.com.chfb.ativo.assetmanagement.dto.DashboardPositionResponse;
import br.com.chfb.ativo.assetmanagement.entity.PortfolioSnapshot;
import br.com.chfb.ativo.assetmanagement.entity.WalletPosition;
import br.com.chfb.ativo.assetmanagement.repository.PortfolioSnapshotRepository;
import br.com.chfb.ativo.assetmanagement.repository.WalletPositionRepository;
import br.com.chfb.ativo.sharedkernel.market.QuotesCollectedEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Carteiras sem posição aberta não geram ponto: não há patrimônio a registrar. */
@Component
public class PortfolioSnapshotGenerator {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSnapshotGenerator.class);

    private final WalletPositionRepository positionRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioValuation valuation;

    public PortfolioSnapshotGenerator(
            WalletPositionRepository positionRepository,
            PortfolioSnapshotRepository snapshotRepository,
            PortfolioValuation valuation) {
        this.positionRepository = positionRepository;
        this.snapshotRepository = snapshotRepository;
        this.valuation = valuation;
    }

    @EventListener
    @Transactional
    public void onQuotesCollected(QuotesCollectedEvent event) {
        List<PortfolioSnapshot> snapshots = positionRepository.findAllActive().stream()
                .collect(Collectors.groupingBy(WalletPosition::getWalletId))
                .entrySet()
                .stream()
                .map(entry -> snapshot(entry.getKey(), entry.getValue(), event))
                .toList();
        snapshotRepository.saveAll(snapshots);
        log.info("Snapshots patrimoniais gerados: {}", snapshots.size());
    }

    private PortfolioSnapshot snapshot(UUID walletId, List<WalletPosition> positions, QuotesCollectedEvent event) {
        List<DashboardPositionResponse> valued = valuation.value(positions);
        return new PortfolioSnapshot(
                walletId,
                sum(valued, DashboardPositionResponse::investedValue),
                sum(valued, DashboardPositionResponse::marketValue),
                event.collectedAt());
    }

    private static BigDecimal sum(
            List<DashboardPositionResponse> positions, Function<DashboardPositionResponse, BigDecimal> field) {
        return positions.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
