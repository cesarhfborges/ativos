-- Ativos que o usuário acompanha sem precisar comprá-los, com o plano dele para cada um.
-- Uma linha por usuário e ativo. Nada aqui entra no patrimônio ou na rentabilidade.
CREATE TABLE watchlist_items (
    id         UUID           PRIMARY KEY,
    user_id    UUID           NOT NULL REFERENCES users (id),
    asset_id   UUID           NOT NULL REFERENCES assets (id),
    buy_below  NUMERIC(24, 8) CHECK (buy_below > 0),
    sell_above NUMERIC(24, 8) CHECK (sell_above > 0),
    stop_loss  NUMERIC(24, 8) CHECK (stop_loss > 0),
    note       VARCHAR(500),
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL,
    UNIQUE (user_id, asset_id)
);

-- A coleta procura os ativos acompanhados por qualquer usuário.
CREATE INDEX watchlist_items_asset_idx ON watchlist_items (asset_id);
