-- Oito casas, como em market_quotes: com quatro, criptos muito baratas não cabem ou perdem o preço.
-- Taxas e totais continuam com quatro casas.
ALTER TABLE transactions ALTER COLUMN unit_price TYPE NUMERIC(24, 8);
ALTER TABLE wallet_positions ALTER COLUMN average_price TYPE NUMERIC(24, 8);
