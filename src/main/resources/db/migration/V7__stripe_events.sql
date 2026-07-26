-- =============================================================================
-- V7 — Idempotência do webhook Stripe
-- =============================================================================

CREATE TABLE IF NOT EXISTS stripe_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id     VARCHAR(255) NOT NULL,
    event_type   VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_stripe_events_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_stripe_events_processed_at ON stripe_events (processed_at);
