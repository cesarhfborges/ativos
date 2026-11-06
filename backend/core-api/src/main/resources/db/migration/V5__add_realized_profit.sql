-- Resultado da venda sobre o preço médio, já descontada a taxa. Só existe em SELL; pode ser negativo.
ALTER TABLE transactions ADD COLUMN realized_profit NUMERIC(18, 4);

ALTER TABLE transactions ADD CONSTRAINT transactions_realized_profit_only_on_sell CHECK (
    (type = 'BUY' AND realized_profit IS NULL) OR (type = 'SELL' AND realized_profit IS NOT NULL)
);
