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

/** Registro imutável do extrato. */
@Entity
@Table(name = "transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", updatable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionType type;

    @Column(precision = 24, scale = 8, updatable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 18, scale = 4, updatable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal fee;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    protected WalletTransaction() {
    }

    private WalletTransaction(
            UUID walletId,
            Asset asset,
            TransactionType type,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal fee,
            BigDecimal totalAmount) {
        this.walletId = walletId;
        this.asset = asset;
        this.type = type;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.fee = fee;
        this.totalAmount = totalAmount;
        // O PostgreSQL guarda microssegundos: assim a resposta mostra o mesmo valor do extrato.
        this.timestamp = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public static WalletTransaction deposit(UUID walletId, BigDecimal amount) {
        return cashMovement(walletId, TransactionType.DEPOSIT, amount);
    }

    public static WalletTransaction withdraw(UUID walletId, BigDecimal amount) {
        return cashMovement(walletId, TransactionType.WITHDRAW, amount);
    }

    public static WalletTransaction buy(
            UUID walletId, Asset asset, BigDecimal quantity, BigDecimal unitPrice, BigDecimal fee, BigDecimal total) {
        return new WalletTransaction(walletId, asset, TransactionType.BUY, quantity, unitPrice, fee, total);
    }

    public static WalletTransaction sell(
            UUID walletId, Asset asset, BigDecimal quantity, BigDecimal unitPrice, BigDecimal fee, BigDecimal total) {
        return new WalletTransaction(walletId, asset, TransactionType.SELL, quantity, unitPrice, fee, total);
    }

    private static WalletTransaction cashMovement(UUID walletId, TransactionType type, BigDecimal amount) {
        return new WalletTransaction(walletId, null, type, null, null, Amounts.money(BigDecimal.ZERO), amount);
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

    public Instant getTimestamp() {
        return timestamp;
    }
}
