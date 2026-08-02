-- =============================================================================
-- V21 — Deep link opcional em notificações (ex.: aceitar convite de viagem)
-- =============================================================================

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS deep_link VARCHAR(512);
