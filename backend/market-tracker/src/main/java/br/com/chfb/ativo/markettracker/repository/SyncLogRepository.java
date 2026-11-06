package br.com.chfb.ativo.markettracker.repository;

import br.com.chfb.ativo.markettracker.dto.SyncItemResponse;
import br.com.chfb.ativo.markettracker.dto.SyncRunResponse;
import br.com.chfb.ativo.markettracker.entity.SyncItemKind;
import br.com.chfb.ativo.markettracker.entity.SyncItemStatus;
import br.com.chfb.ativo.markettracker.entity.SyncOrigin;
import br.com.chfb.ativo.markettracker.entity.SyncRunStatus;
import br.com.chfb.ativo.sharedkernel.portfolio.AssetClass;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Em SQL direto: é só escrita e leitura de registros, e a busca por símbolo olha a tabela filha. */
@Repository
public class SyncLogRepository {

    /** A busca de um símbolo novo cobre um único ativo: não conta como sincronização geral. */
    private static final String GENERAL_RUN = "origin <> 'NEW_SYMBOL'";
    private static final int MESSAGE_LIMIT = 500;

    private static final RowMapper<SyncRunResponse> RUN = (row, number) -> new SyncRunResponse(
            row.getObject("id", UUID.class),
            SyncOrigin.valueOf(row.getString("origin")),
            SyncRunStatus.valueOf(row.getString("status")),
            row.getTimestamp("started_at").toInstant(),
            instant(row.getTimestamp("finished_at")),
            row.getInt("requested_symbols"),
            row.getInt("updated_symbols"),
            row.getString("message"));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public SyncLogRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    public void insertRunning(UUID id, SyncOrigin origin, Instant startedAt) {
        jdbc.update(
                "INSERT INTO sync_runs (id, origin, status, started_at) VALUES (?, ?, 'RUNNING', ?)",
                id,
                origin.name(),
                Timestamp.from(startedAt));
    }

    /** O resumo e os itens são gravados juntos. */
    @Transactional
    public void finish(
            UUID id,
            SyncRunStatus status,
            Instant finishedAt,
            int requested,
            int updated,
            String message,
            List<SyncItemResponse> items) {
        jdbc.update(
                """
                UPDATE sync_runs
                SET status = ?, finished_at = ?, requested_symbols = ?, updated_symbols = ?, message = ?
                WHERE id = ?
                """,
                status.name(),
                Timestamp.from(finishedAt),
                requested,
                updated,
                limited(message),
                id);
        jdbc.batchUpdate(
                """
                INSERT INTO sync_run_items (run_id, symbol, asset_class, kind, status, points, message)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (run_id, symbol, kind) DO NOTHING
                """,
                items,
                Math.max(items.size(), 1),
                (statement, item) -> {
                    statement.setObject(1, id);
                    statement.setString(2, item.symbol());
                    statement.setString(3, item.assetClass().name());
                    statement.setString(4, item.kind().name());
                    statement.setString(5, item.status().name());
                    statement.setObject(6, item.points());
                    statement.setString(7, limited(item.message()));
                });
    }

    /** Execuções que a parada da aplicação deixou pela metade. */
    public int markInterrupted(Instant at) {
        return jdbc.update(
                "UPDATE sync_runs SET status = 'INTERRUPTED', finished_at = ? WHERE status = 'RUNNING'",
                Timestamp.from(at));
    }

    public int deleteStartedBefore(Instant limit) {
        return jdbc.update("DELETE FROM sync_runs WHERE started_at < ?", Timestamp.from(limit));
    }

    public Instant findLastSyncAt() {
        return instant(jdbc.queryForObject(
                "SELECT max(finished_at) FROM sync_runs WHERE " + GENERAL_RUN + " AND status IN ('SUCCESS', 'PARTIAL')",
                Timestamp.class));
    }

    public Instant findLastGeneralRunStartedAt() {
        return instant(jdbc.queryForObject("SELECT max(started_at) FROM sync_runs WHERE " + GENERAL_RUN, Timestamp.class));
    }

    public boolean lastGeneralRunFailed() {
        List<String> statuses = jdbc.queryForList(
                """
                SELECT status FROM sync_runs
                WHERE %s AND status NOT IN ('RUNNING', 'INTERRUPTED')
                ORDER BY started_at DESC
                LIMIT 1
                """.formatted(GENERAL_RUN),
                String.class);
        return !statuses.isEmpty() && !SyncRunStatus.SUCCESS.name().equals(statuses.get(0));
    }

    public List<SyncRunResponse> findRuns(SyncOrigin origin, SyncRunStatus status, String symbol, int page, int size) {
        MapSqlParameterSource parameters = filters(origin, status, symbol)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        return namedJdbc.query(
                "SELECT * FROM sync_runs " + where(origin, status, symbol) + " ORDER BY started_at DESC LIMIT :limit OFFSET :offset",
                parameters,
                RUN);
    }

    public long countRuns(SyncOrigin origin, SyncRunStatus status, String symbol) {
        Long total = namedJdbc.queryForObject(
                "SELECT count(*) FROM sync_runs " + where(origin, status, symbol), filters(origin, status, symbol), Long.class);
        return total == null ? 0 : total;
    }

    /** Falhas primeiro: são o motivo de abrir o detalhe. */
    public List<SyncItemResponse> findItems(UUID runId) {
        return jdbc.query(
                """
                SELECT symbol, asset_class, kind, status, points, message
                FROM sync_run_items
                WHERE run_id = ?
                ORDER BY CASE status WHEN 'FAILED' THEN 0 WHEN 'NO_DATA' THEN 1 ELSE 2 END, symbol, kind DESC
                """,
                (row, number) -> new SyncItemResponse(
                        row.getString("symbol"),
                        AssetClass.valueOf(row.getString("asset_class")),
                        SyncItemKind.valueOf(row.getString("kind")),
                        SyncItemStatus.valueOf(row.getString("status")),
                        row.getObject("points", Integer.class),
                        row.getString("message")),
                runId);
    }

    private static String where(SyncOrigin origin, SyncRunStatus status, String symbol) {
        List<String> conditions = new ArrayList<>();
        if (origin != null) {
            conditions.add("origin = :origin");
        }
        if (status != null) {
            conditions.add("status = :status");
        }
        if (symbol != null) {
            conditions.add("EXISTS (SELECT 1 FROM sync_run_items i WHERE i.run_id = id AND i.symbol = :symbol)");
        }
        return conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
    }

    private static MapSqlParameterSource filters(SyncOrigin origin, SyncRunStatus status, String symbol) {
        return new MapSqlParameterSource()
                .addValue("origin", origin == null ? null : origin.name())
                .addValue("status", status == null ? null : status.name())
                .addValue("symbol", symbol);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String limited(String message) {
        return message == null || message.length() <= MESSAGE_LIMIT ? message : message.substring(0, MESSAGE_LIMIT);
    }
}
