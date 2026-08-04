-- =============================================================================
-- V24 — Tracking de abertura da proposta pública (atividade em tempo real)
-- =============================================================================

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS proposal_last_viewed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS proposal_view_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS proposal_views_today INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS proposal_views_day DATE;
