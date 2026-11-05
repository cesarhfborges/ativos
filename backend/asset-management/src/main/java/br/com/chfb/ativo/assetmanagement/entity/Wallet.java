package br.com.chfb.ativo.assetmanagement.entity;

import br.com.chfb.ativo.assetmanagement.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "fiat_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal fiatBalance;

    protected Wallet() {
    }

    public Wallet(UUID userId, String name) {
        this.userId = userId;
        this.name = name;
        this.fiatBalance = Amounts.money(BigDecimal.ZERO);
    }

    public void credit(BigDecimal amount) {
        fiatBalance = Amounts.money(fiatBalance.add(amount));
    }

    public void debit(BigDecimal amount) {
        if (!hasBalanceFor(amount)) {
            throw new BusinessRuleException("Saldo em caixa insuficiente");
        }
        fiatBalance = Amounts.money(fiatBalance.subtract(amount));
    }

    public boolean hasBalanceFor(BigDecimal amount) {
        return fiatBalance.compareTo(amount) >= 0;
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getFiatBalance() {
        return fiatBalance;
    }
}
