package br.com.chfb.ativo.markettracker.repository;

import br.com.chfb.ativo.sharedkernel.market.SentimentScore;
import br.com.chfb.ativo.sharedkernel.market.SentimentStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MarketSentimentRepository implements SentimentStore {

    private static final int BATCH_SIZE = 500;
    private static final int SCORE_SCALE = 2;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public MarketSentimentRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    @Override
    public Map<String, LocalDate> findLastDays(Collection<String> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        Map<String, LocalDate> days = new HashMap<>();
        namedJdbc.query(
                "SELECT symbol, max(day) AS last_day FROM market_sentiment_daily WHERE symbol IN (:symbols) GROUP BY symbol",
                new MapSqlParameterSource("symbols", symbols),
                row -> {
                    days.put(row.getString("symbol"), row.getDate("last_day").toLocalDate());
                });
        return days;
    }

    @Override
    public void save(List<SentimentScore> scores, Instant computedAt) {
        jdbc.batchUpdate(
                """
                INSERT INTO market_sentiment_daily
                    (symbol, day, score, rsi_score, trend_score, range_score, volatility_score, computed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (symbol, day) DO UPDATE SET
                    score = EXCLUDED.score,
                    rsi_score = EXCLUDED.rsi_score,
                    trend_score = EXCLUDED.trend_score,
                    range_score = EXCLUDED.range_score,
                    volatility_score = EXCLUDED.volatility_score,
                    computed_at = EXCLUDED.computed_at
                """,
                scores,
                BATCH_SIZE,
                (statement, score) -> {
                    statement.setString(1, score.symbol());
                    statement.setDate(2, Date.valueOf(score.day()));
                    statement.setBigDecimal(3, scaled(score.score()));
                    statement.setBigDecimal(4, scaled(score.rsi()));
                    statement.setBigDecimal(5, scaled(score.trend()));
                    statement.setBigDecimal(6, scaled(score.range()));
                    statement.setBigDecimal(7, scaled(score.volatility()));
                    statement.setTimestamp(8, Timestamp.from(computedAt));
                });
    }

    @Override
    public Map<String, List<SentimentScore>> findSince(Collection<String> symbols, LocalDate from) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        Map<String, List<SentimentScore>> scores = new LinkedHashMap<>();
        namedJdbc.query(
                """
                SELECT symbol, day, score, rsi_score, trend_score, range_score, volatility_score
                FROM market_sentiment_daily
                WHERE symbol IN (:symbols) AND day >= :from
                ORDER BY symbol, day
                """,
                new MapSqlParameterSource().addValue("symbols", symbols).addValue("from", Date.valueOf(from)),
                row -> {
                    String symbol = row.getString("symbol");
                    scores.computeIfAbsent(symbol, key -> new ArrayList<>()).add(new SentimentScore(
                            symbol,
                            row.getDate("day").toLocalDate(),
                            row.getDouble("score"),
                            row.getDouble("rsi_score"),
                            row.getDouble("trend_score"),
                            row.getDouble("range_score"),
                            row.getDouble("volatility_score")));
                });
        return scores;
    }

    private static BigDecimal scaled(double value) {
        return BigDecimal.valueOf(value).setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
