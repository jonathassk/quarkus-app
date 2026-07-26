-- =============================================================================
-- V8 — Desbloqueios por viagem (pagamento UNITARIO)
-- =============================================================================
-- Uma compra UNITARIO grava uma linha por benefício liberado (EXPORT_PDF e
-- AI_GENERATIONS), compartilhando o mesmo stripe_session_id.

CREATE TABLE IF NOT EXISTS trip_unlocks (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id           UUID           NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id           UUID           REFERENCES users(id) ON DELETE SET NULL,
    kind              VARCHAR(32)    NOT NULL,
    amount            NUMERIC(12, 2),
    currency          VARCHAR(3),
    stripe_session_id VARCHAR(255),
    paid_at           TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_trip_unlocks_trip_kind ON trip_unlocks (trip_id, kind);
CREATE INDEX IF NOT EXISTS idx_trip_unlocks_session ON trip_unlocks (stripe_session_id);
CREATE INDEX IF NOT EXISTS idx_trip_unlocks_user ON trip_unlocks (user_id);
