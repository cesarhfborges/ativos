-- Se o provedor de cotações confirmou o símbolo. Os ativos já cadastrados nunca foram conferidos.
ALTER TABLE assets
    ADD COLUMN verification_status VARCHAR(12) NOT NULL DEFAULT 'UNVERIFIED'
        CHECK (verification_status IN ('VERIFIED', 'UNVERIFIED'));
