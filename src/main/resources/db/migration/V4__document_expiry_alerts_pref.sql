-- =============================================================================
-- V4 — Preferência para ligar/desligar os alertas de expiração de documentos
-- =============================================================================

ALTER TABLE user_email_preferences
    ADD COLUMN IF NOT EXISTS document_expiry_alerts BOOLEAN NOT NULL DEFAULT TRUE;
