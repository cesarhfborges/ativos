package br.com.chfb.ativo.markettracker.repository;

import br.com.chfb.ativo.sharedkernel.market.PricePoint;
import br.com.chfb.ativo.sharedkernel.market.PriceResolution;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Em SQL direto: a tabela não tem identidade própria e as consultas dependem de DISTINCT ON. */
@Repository
public class MarketQuoteHistoryRepository {

    /** O campo vai fixo no texto: como parâmetro, o Postgres não reconhece o DISTINCT ON e o ORDER BY como iguais. */
    private static final String HISTORY_SQL = """
            SELECT DISTINCT ON (symbol, date_trunc('%1$s', quoted_at)) symbol, quoted_at, price
            FROM market_quote_history
            WHERE symbol IN (:symbols) AND quoted_at >= :from
            ORDER BY symbol, date_trunc('%1$s', quoted_at), quoted_at DESC
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public MarketQuoteHistoryRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    public void insert(String symbol, List<PricePoint> points) {
        jdbc.batchUpdate(
                """
                INSERT INTO market_quote_history (symbol, quoted_at, price)
                VALUES (?, ?, ?)
                ON CONFLICT (symbol, quoted_at) DO NOTHING
                """,
                points,
                points.size(),
                (statement, point) -> {
                    statement.setString(1, symbol);
                    statement.setTimestamp(2, Timestamp.from(point.time()));
                    statement.setBigDecimal(3, point.price());
                });
    }

    public Map<String, List<PricePoint>> findHistory(Collection<String> symbols, Instant from, PriceResolution resolution) {
        String field = resolution == PriceResolution.HOUR ? "hour" : "day";
        Map<String, List<PricePoint>> history = new LinkedHashMap<>();
        namedJdbc.query(
                HISTORY_SQL.formatted(field),
                new MapSqlParameterSource().addValue("symbols", symbols).addValue("from", Timestamp.from(from)),
                row -> {
                    history.computeIfAbsent(row.getString("symbol"), symbol -> new ArrayList<>())
                            .add(new PricePoint(row.getTimestamp("quoted_at").toInstant(), row.getBigDecimal("price")));
                });
        return history;
    }

    public Map<String, BigDecimal> findPricesAt(Collection<String> symbols, Instant at) {
        Map<String, BigDecimal> prices = new HashMap<>();
        namedJdbc.query(
                """
                SELECT DISTINCT ON (symbol) symbol, price
                FROM market_quote_history
                WHERE symbol IN (:symbols) AND quoted_at <= :at
                ORDER BY symbol, quoted_at DESC
                """,
                new MapSqlParameterSource().addValue("symbols", symbols).addValue("at", Timestamp.from(at)),
                row -> {
                    prices.put(row.getString("symbol"), row.getBigDecimal("price"));
                });
        return prices;
    }

    public Map<String, List<PricePoint>> findPricesBetween(Collection<String> symbols, Instant from, Instant to) {
        Map<String, List<PricePoint>> prices = new HashMap<>();
        namedJdbc.query(
                """
                SELECT symbol, quoted_at, price
                FROM market_quote_history
                WHERE symbol IN (:symbols) AND quoted_at BETWEEN :from AND :to
                """,
                new MapSqlParameterSource()
                        .addValue("symbols", symbols)
                        .addValue("from", Timestamp.from(from))
                        .addValue("to", Timestamp.from(to)),
                row -> {
                    prices.computeIfAbsent(row.getString("symbol"), symbol -> new ArrayList<>())
                            .add(new PricePoint(row.getTimestamp("quoted_at").toInstant(), row.getBigDecimal("price")));
                });
        return prices;
    }

    /** Símbolos com algum registro até {@code oldest} e o mais recente a partir de {@code latest}. */
    public Set<String> findSymbolsWithHistorySince(Collection<String> symbols, Instant oldest, Instant latest) {
        return new HashSet<>(namedJdbc.queryForList(
                """
                SELECT symbol FROM market_quote_history
                WHERE symbol IN (:symbols)
                GROUP BY symbol
                HAVING min(quoted_at) <= :oldest AND max(quoted_at) >= :latest
                """,
                new MapSqlParameterSource()
                        .addValue("symbols", symbols)
                        .addValue("oldest", Timestamp.from(oldest))
                        .addValue("latest", Timestamp.from(latest)),
                String.class));
    }
}
