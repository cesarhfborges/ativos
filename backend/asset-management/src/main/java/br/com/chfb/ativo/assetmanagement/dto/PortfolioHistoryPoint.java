package br.com.chfb.ativo.assetmanagement.dto;

import br.com.chfb.ativo.assetmanagement.entity.PortfolioSnapshot;
import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioHistoryPoint(Instant time, BigDecimal investedBalance, BigDecimal totalBalance) {

    public static PortfolioHistoryPoint from(PortfolioSnapshot snapshot) {
        return new PortfolioHistoryPoint(
                snapshot.getSnapshotTime(), snapshot.getInvestedBalance(), snapshot.getTotalBalance());
    }
}
