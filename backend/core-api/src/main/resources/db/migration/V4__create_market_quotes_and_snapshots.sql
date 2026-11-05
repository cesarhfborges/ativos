-- Só a última cotação de cada símbolo. Oito casas: criptos baratas perderiam o preço com quatro.
CREATE TABLE market_quotes (
    id              UUID           PRIMARY KEY,
    symbol          VARCHAR(20)    NOT NULL UNIQUE,
    price           NUMERIC(24, 8) NOT NULL CHECK (price >= 0),
    last_updated_at TIMESTAMPTZ    NOT NULL
);

-- invested_balance é o custo das posições (preço médio); total_balance, o valor de mercado na coleta.
CREATE TABLE portfolio_snapshots (
    id               UUID           PRIMARY KEY,
    wallet_id        UUID           NOT NULL REFERENCES wallets (id),
    invested_balance NUMERIC(24, 4) NOT NULL,
    total_balance    NUMERIC(24, 4) NOT NULL,
    snapshot_time    TIMESTAMPTZ    NOT NULL
);

CREATE INDEX portfolio_snapshots_wallet_time_idx ON portfolio_snapshots (wallet_id, snapshot_time);
