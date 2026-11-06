-- Estorno: a operação continua no histórico, mas deixa de contar na posição e no lucro.
ALTER TABLE transactions ADD COLUMN reversed_at TIMESTAMPTZ;

-- Consulta usada para refazer a posição a partir das operações válidas.
CREATE INDEX transactions_wallet_asset_active_idx ON transactions (wallet_id, asset_id, timestamp)
    WHERE reversed_at IS NULL;
