-- =============================================================================
-- V13 — Central de notificações in-app + preferências por canal
-- =============================================================================

CREATE TABLE IF NOT EXISTS notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind         VARCHAR(32)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    body         TEXT,
    entity_type  VARCHAR(32),
    entity_id    UUID,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_read
    ON notifications (user_id, read_at);

-- Preferências por canal (in-app e e-mails de atividade).
-- document_expiry_alerts permanece o toggle específico de DOC_EXPIRING por e-mail.
ALTER TABLE user_email_preferences
    ADD COLUMN IF NOT EXISTS in_app_notifications BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE user_email_preferences
    ADD COLUMN IF NOT EXISTS activity_emails BOOLEAN NOT NULL DEFAULT TRUE;
