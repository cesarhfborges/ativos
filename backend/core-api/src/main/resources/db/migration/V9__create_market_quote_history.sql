-- Preços ao longo do tempo, para os gráficos de mercado. market_quotes continua só com o último preço.
-- Recebe os fechamentos diários da carga inicial e o preço de cada coleta.
CREATE TABLE market_quote_history (
    symbol    VARCHAR(20)    NOT NULL,
    quoted_at TIMESTAMPTZ    NOT NULL,
    price     NUMERIC(24, 8) NOT NULL CHECK (price >= 0),
    PRIMARY KEY (symbol, quoted_at)
);
