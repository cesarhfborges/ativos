-- Índice de ganância e medo por símbolo e dia, derivado de market_quote_history: pode ser apagado e é refeito
-- na coleta seguinte. A nota da carteira não fica aqui, porque depende dos pesos atuais e é calculada na leitura.
CREATE TABLE market_sentiment_daily (
    symbol           VARCHAR(20)  NOT NULL,
    day              DATE         NOT NULL,
    score            NUMERIC(5, 2) NOT NULL CHECK (score BETWEEN 0 AND 100),
    rsi_score        NUMERIC(5, 2) NOT NULL,
    trend_score      NUMERIC(5, 2) NOT NULL,
    range_score      NUMERIC(5, 2) NOT NULL,
    volatility_score NUMERIC(5, 2) NOT NULL,
    computed_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (symbol, day)
);
