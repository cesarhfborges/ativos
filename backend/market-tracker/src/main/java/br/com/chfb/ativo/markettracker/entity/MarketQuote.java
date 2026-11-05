package br.com.chfb.ativo.markettracker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Gravada apenas por {@code MarketQuoteRepository.upsert}: guarda só a última cotação de cada símbolo. */
@Entity
@Table(name = "market_quotes")
public class MarketQuote {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String symbol;

    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal price;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    protected MarketQuote() {
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }
}
