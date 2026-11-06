package br.com.chfb.ativo.assetmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Registro do histórico de compras e vendas. Os dados da operação nunca mudam: um erro se corrige com
 * estorno, e só o lucro realizado, que depende do preço médio, pode ser recalculado.
 */
@Entity
@Table(name = "transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false, updatable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 24, scale = 8, updatable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 24, scale = 8, updatable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal fee;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal totalAmount;

    /** Só em vendas: valor líquido menos o custo da quantidade vendida. */
    @Column(name = "realized_profit", precision = 18, scale = 4)
    private BigDecimal realizedProfit;

    /** Quando a operação aconteceu, que pode ser antes de ela ser lançada. */
    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    protected WalletTransaction() {
    }

    private WalletTransaction(
            UUID walletId,
            Asset asset,
            TransactionType type,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal fee,
            BigDecimal totalAmount,
            BigDecimal realizedProfit,
            Instant executedAt) {
        this.walletId = walletId;
        this.asset = asset;
        this.type = type;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.fee = fee;
        this.totalAmount = totalAmount;
        this.realizedProfit = realizedProfit;
        // O PostgreSQL guarda microssegundos: assim a resposta mostra o mesmo valor do extrato.
        this.recordedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.timestamp = executedAt == null ? recordedAt : executedAt.truncatedTo(ChronoUnit.MICROS);
    }

    public static WalletTransaction buy(
            UUID walletId,
            Asset asset,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal fee,
            BigDecimal total,
            Instant executedAt) {
        return new WalletTransaction(walletId, asset, TransactionType.BUY, quantity, unitPrice, fee, total, null, executedAt);
    }

    public static WalletTransaction sell(
            UUID walletId,
            Asset asset,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal fee,
            BigDecimal total,
            BigDecimal realizedProfit,
            Instant executedAt) {
        return new WalletTransaction(
                walletId, asset, TransactionType.SELL, quantity, unitPrice, fee, total, realizedProfit, executedAt);
    }

    public void markReversed() {
        reversedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void updateRealizedProfit(BigDecimal realizedProfit) {
        this.realizedProfit = realizedProfit;
    }

    public boolean isReversed() {
        return reversedAt != null;
    }

    /** Já estava lançada em {@code time}, ainda que tenha sido estornada depois. */
    public boolean wasRecordedBy(Instant time) {
        return !recordedAt.isAfter(time);
    }

    /** Contava como válida em {@code time}, mas foi estornada depois. */
    public boolean wasReversedAfter(Instant time) {
        return wasRecordedBy(time) && reversedAt != null && reversedAt.isAfter(time);
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public UUID getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getRealizedProfit() {
        return realizedProfit;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
