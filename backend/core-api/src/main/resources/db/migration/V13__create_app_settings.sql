-- Só os valores alterados pelo admin; os demais vêm do application.yml. A chave é a da propriedade.
CREATE TABLE app_settings (
    key        VARCHAR(80)  PRIMARY KEY,
    value      VARCHAR(40)  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    updated_by VARCHAR(254) NOT NULL
);
