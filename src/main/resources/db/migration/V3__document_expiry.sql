-- =============================================================================
-- V3 — Documentos com data de validade (alertas de expiração)
-- =============================================================================

CREATE TABLE IF NOT EXISTS user_document_expiry (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind          VARCHAR(32)  NOT NULL DEFAULT 'CUSTOM', -- PASSPORT | VISA | INTERNATIONAL_LICENSE | CUSTOM
    name          VARCHAR(255),                            -- obrigatório quando kind = CUSTOM; nulo nos fixos (front traduz)
    expiry_date   DATE,
    alert_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- No máximo um registro por tipo fixo (passaporte/visto/CNH) por usuário.
-- Documentos CUSTOM não têm esse limite (usuário pode adicionar quantos quiser).
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_document_expiry_fixed_kind
    ON user_document_expiry (user_id, kind)
    WHERE kind <> 'CUSTOM';

CREATE INDEX IF NOT EXISTS idx_user_document_expiry_user
    ON user_document_expiry (user_id);

-- Usado pelo email-worker para varrer documentos perto do vencimento.
CREATE INDEX IF NOT EXISTS idx_user_document_expiry_date
    ON user_document_expiry (expiry_date)
    WHERE expiry_date IS NOT NULL AND alert_enabled = TRUE;
