package br.com.chfb.ativo.assetmanagement.entity;

import br.com.chfb.ativo.assetmanagement.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wallet_positions")
public class WalletPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false, updatable = false)
    private Asset asset;

    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal quantity;

    @Column(name = "average_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal averagePrice;

    protected WalletPosition() {
    }

    public WalletPosition(UUID walletId, Asset asset) {
        this.walletId = walletId;
        this.asset = asset;
        this.quantity = Amounts.quantity(BigDecimal.ZERO);
        this.averagePrice = Amounts.money(BigDecimal.ZERO);
    }

    /** Com a posição zerada, a fórmula resulta no próprio preço da compra. A taxa não entra no preço médio. */
    public void addPurchase(BigDecimal purchasedQuantity, BigDecimal unitPrice) {
        BigDecimal newQuantity = Amounts.quantity(quantity.add(purchasedQuantity));
        BigDecimal totalCost = quantity.multiply(averagePrice).add(purchasedQuantity.multiply(unitPrice));
        averagePrice = Amounts.moneyQuotient(totalCost, newQuantity);
        quantity = newQuantity;
    }

    /** O preço médio não muda na venda: ela só reduz a quantidade em custódia. */
    public void removeSold(BigDecimal soldQuantity) {
        if (!holds(soldQuantity)) {
            throw new BusinessRuleException("Quantidade em custódia insuficiente");
        }
        quantity = Amounts.quantity(quantity.subtract(soldQuantity));
    }

    /** Assume o resultado de um recálculo feito a partir do histórico. */
    public void replaceWith(WalletPosition recalculated) {
        quantity = recalculated.quantity;
        averagePrice = recalculated.averagePrice;
    }

    public boolean holds(BigDecimal requestedQuantity) {
        return quantity.compareTo(requestedQuantity) >= 0;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public Asset getAsset() {
        return asset;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }
}
