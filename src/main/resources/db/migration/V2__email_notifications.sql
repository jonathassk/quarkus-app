-- =============================================================================
-- V2 — Preferências de e-mail + log de entrega (idempotência)
-- =============================================================================

CREATE TABLE IF NOT EXISTS user_email_preferences (
    user_id         UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    email_updates   BOOLEAN NOT NULL DEFAULT TRUE,
    trip_reminders  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS email_notification_log (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email_type   VARCHAR(64)  NOT NULL,
    reference_id UUID,
    channel      VARCHAR(16)  NOT NULL DEFAULT 'EMAIL',
    message_id   VARCHAR(256),
    status       VARCHAR(32)  NOT NULL DEFAULT 'SENT',
    sent_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Evita reenvio do mesmo lembrete/produto para o mesmo usuário
CREATE UNIQUE INDEX IF NOT EXISTS uk_email_notification_idempotent
    ON email_notification_log (
        user_id,
        email_type,
        (COALESCE(reference_id, '00000000-0000-0000-0000-000000000000'::uuid))
    );

CREATE INDEX IF NOT EXISTS idx_email_notification_log_sent_at
    ON email_notification_log (sent_at DESC);

CREATE INDEX IF NOT EXISTS idx_email_notification_log_type
    ON email_notification_log (email_type, sent_at DESC);
