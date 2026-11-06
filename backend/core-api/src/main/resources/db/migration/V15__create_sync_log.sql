-- Cada execução da coleta de cotações. Registros antigos são apagados conforme a retenção configurada.
CREATE TABLE sync_runs (
    id                UUID        PRIMARY KEY,
    origin            VARCHAR(20) NOT NULL CHECK (origin IN ('SCHEDULED', 'STARTUP', 'ADMIN', 'NEW_SYMBOL')),
    status            VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED', 'INTERRUPTED')),
    started_at        TIMESTAMPTZ NOT NULL,
    finished_at       TIMESTAMPTZ,
    requested_symbols INTEGER     NOT NULL DEFAULT 0,
    updated_symbols   INTEGER     NOT NULL DEFAULT 0,
    message           VARCHAR(500)
);

CREATE INDEX sync_runs_started_at_idx ON sync_runs (started_at DESC);

-- O resultado de cada símbolo: a cotação e, quando houve, a carga de histórico.
CREATE TABLE sync_run_items (
    run_id      UUID        NOT NULL REFERENCES sync_runs (id) ON DELETE CASCADE,
    symbol      VARCHAR(20) NOT NULL,
    asset_class VARCHAR(10) NOT NULL,
    kind        VARCHAR(10) NOT NULL CHECK (kind IN ('QUOTE', 'HISTORY')),
    status      VARCHAR(10) NOT NULL CHECK (status IN ('UPDATED', 'NO_DATA', 'FAILED')),
    points      INTEGER,
    message     VARCHAR(500),
    PRIMARY KEY (run_id, symbol, kind)
);

CREATE INDEX sync_run_items_symbol_idx ON sync_run_items (symbol);
