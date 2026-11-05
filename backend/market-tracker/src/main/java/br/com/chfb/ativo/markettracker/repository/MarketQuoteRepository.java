package br.com.chfb.ativo.markettracker.repository;

import br.com.chfb.ativo.markettracker.entity.MarketQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MarketQuoteRepository extends JpaRepository<MarketQuote, UUID> {

    List<MarketQuote> findBySymbolIn(Collection<String> symbols);

    @Transactional
    @Modifying
    @Query(
            value = """
                    INSERT INTO market_quotes (id, symbol, price, last_updated_at)
                    VALUES (:id, :symbol, :price, :updatedAt)
                    ON CONFLICT (symbol) DO UPDATE
                    SET price = EXCLUDED.price, last_updated_at = EXCLUDED.last_updated_at
                    """,
            nativeQuery = true)
    void upsert(
            @Param("id") UUID id,
            @Param("symbol") String symbol,
            @Param("price") BigDecimal price,
            @Param("updatedAt") Instant updatedAt);
}
