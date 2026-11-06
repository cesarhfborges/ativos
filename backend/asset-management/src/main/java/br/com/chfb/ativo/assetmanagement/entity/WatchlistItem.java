package br.com.chfb.ativo.assetmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Um ativo acompanhado e o plano do usuário para ele. Todos os preços são opcionais. */
@Entity
@Table(name = "watchlist_items")
public class WatchlistItem {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "buy_below")
    private BigDecimal buyBelow;

    @Column(name = "sell_above")
    private BigDecimal sellAbove;

    @Column(name = "stop_loss")
    private BigDecimal stopLoss;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WatchlistItem() {
    }

    public WatchlistItem(UUID userId, Asset asset, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.asset = asset;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changePlan(BigDecimal buyBelow, BigDecimal sellAbove, BigDecimal stopLoss, String note, Instant now) {
        this.buyBelow = buyBelow;
        this.sellAbove = sellAbove;
        this.stopLoss = stopLoss;
        this.note = note;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public BigDecimal getBuyBelow() {
        return buyBelow;
    }

    public BigDecimal getSellAbove() {
        return sellAbove;
    }

    public BigDecimal getStopLoss() {
        return stopLoss;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
