package br.com.chfb.ativo.assetmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Foto da carteira na coleta: {@code investedBalance} é o custo das posições; {@code totalBalance}, o valor de mercado. */
@Entity
@Table(name = "portfolio_snapshots")
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "invested_balance", nullable = false, precision = 24, scale = 4)
    private BigDecimal investedBalance;

    @Column(name = "total_balance", nullable = false, precision = 24, scale = 4)
    private BigDecimal totalBalance;

    @Column(name = "snapshot_time", nullable = false)
    private Instant snapshotTime;

    protected PortfolioSnapshot() {
    }

    public PortfolioSnapshot(UUID walletId, BigDecimal investedBalance, BigDecimal totalBalance, Instant snapshotTime) {
        this.walletId = walletId;
        this.investedBalance = investedBalance;
        this.totalBalance = totalBalance;
        this.snapshotTime = snapshotTime;
    }

    public BigDecimal getInvestedBalance() {
        return investedBalance;
    }

    public BigDecimal getTotalBalance() {
        return totalBalance;
    }

    public Instant getSnapshotTime() {
        return snapshotTime;
    }
}
