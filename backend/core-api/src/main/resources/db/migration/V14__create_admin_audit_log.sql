-- Ações administrativas. Guarda o e-mail do momento, que continua legível se a conta mudar depois.
CREATE TABLE admin_audit_log (
    id          UUID         PRIMARY KEY,
    actor_email VARCHAR(254) NOT NULL,
    action      VARCHAR(40)  NOT NULL,
    target      VARCHAR(254),
    details     VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX admin_audit_log_created_at_idx ON admin_audit_log (created_at DESC);
