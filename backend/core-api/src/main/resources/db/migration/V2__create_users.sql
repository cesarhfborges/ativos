CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    -- Sempre em minúsculas, para que a unicidade não dependa de maiúsculas e minúsculas.
    email         VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(email)),
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);
