-- Dados opcionais do perfil: contas existentes continuam válidas sem preenchê-los.
ALTER TABLE users
    ADD COLUMN phone            VARCHAR(20),
    ADD COLUMN birth_date       DATE,
    ADD COLUMN investor_profile VARCHAR(20)
        CHECK (investor_profile IN ('CONSERVATIVE', 'MODERATE', 'AGGRESSIVE'));
