-- Papel e situação da conta. Quem já existe continua como usuário comum e ativo.
ALTER TABLE users
    ADD COLUMN role          VARCHAR(10) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    ADD COLUMN active        BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN last_login_at TIMESTAMPTZ;
