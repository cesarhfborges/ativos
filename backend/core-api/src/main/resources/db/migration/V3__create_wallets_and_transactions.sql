-- Uma carteira por usuário: os endpoints da Etapa 2 operam sobre "a carteira do usuário logado".
CREATE TABLE wallets (
    id           UUID           PRIMARY KEY,
    user_id      UUID           NOT NULL UNIQUE REFERENCES users (id),
    name         VARCHAR(120)   NOT NULL,
    fiat_balance NUMERIC(18, 4) NOT NULL CHECK (fiat_balance >= 0)
);

-- Catálogo compartilhado entre usuários. As cotações (Etapa 3) também são identificadas só pelo símbolo.
CREATE TABLE assets (
    id         UUID         PRIMARY KEY,
    symbol     VARCHAR(20)  NOT NULL UNIQUE CHECK (symbol = upper(symbol)),
    name       VARCHAR(120) NOT NULL,
    asset_type VARCHAR(10)  NOT NULL CHECK (asset_type IN ('STOCK', 'CRYPTO'))
);

-- Uma posição zerada é mantida: a próxima compra recomeça o preço médio a partir dela.
CREATE TABLE wallet_positions (
    id            UUID           PRIMARY KEY,
    wallet_id     UUID           NOT NULL REFERENCES wallets (id),
    asset_id      UUID           NOT NULL REFERENCES assets (id),
    quantity      NUMERIC(24, 8) NOT NULL CHECK (quantity >= 0),
    average_price NUMERIC(18, 4) NOT NULL CHECK (average_price >= 0),
    UNIQUE (wallet_id, asset_id)
);

CREATE TABLE transactions (
    id           UUID           PRIMARY KEY,
    wallet_id    UUID           NOT NULL REFERENCES wallets (id),
    asset_id     UUID           REFERENCES assets (id),
    type         VARCHAR(10)    NOT NULL CHECK (type IN ('BUY', 'SELL', 'DEPOSIT', 'WITHDRAW')),
    quantity     NUMERIC(24, 8),
    unit_price   NUMERIC(18, 4),
    fee          NUMERIC(18, 4) NOT NULL CHECK (fee >= 0),
    -- Valor que entrou ou saiu do caixa: em BUY inclui a taxa; em SELL já vem descontado dela.
    total_amount NUMERIC(18, 4) NOT NULL CHECK (total_amount >= 0),
    timestamp    TIMESTAMPTZ    NOT NULL,
    CHECK (
        (type IN ('BUY', 'SELL')
            AND asset_id IS NOT NULL AND quantity > 0 AND unit_price > 0)
        OR (type IN ('DEPOSIT', 'WITHDRAW')
            AND asset_id IS NULL AND quantity IS NULL AND unit_price IS NULL)
    )
);

CREATE INDEX transactions_wallet_timestamp_idx ON transactions (wallet_id, timestamp DESC);
