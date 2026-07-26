-- =============================================================================
-- V12 — Entitlements (ai_generations + size_bytes) e pagamentos de proposta
-- =============================================================================

-- Consumo de gerações de IA por usuário/período (e opcionalmente por viagem).
CREATE TABLE IF NOT EXISTS ai_generations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trip_id    UUID         REFERENCES trips(id) ON DELETE SET NULL,
    kind       VARCHAR(32)  NOT NULL DEFAULT 'PLAN',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_generations_user_created
    ON ai_generations (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_generations_trip
    ON ai_generations (trip_id);

-- Tamanho do arquivo para quota agregada de documentos.
ALTER TABLE trip_documents
    ADD COLUMN IF NOT EXISTS size_bytes BIGINT;

-- Pagamentos ligados à proposta B2B (sinal / saldo / valor cheio).
CREATE TABLE IF NOT EXISTS trip_payments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id           UUID           NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    kind              VARCHAR(16)    NOT NULL,
    amount            NUMERIC(12, 2) NOT NULL,
    currency          VARCHAR(3)     NOT NULL DEFAULT 'BRL',
    stripe_session_id VARCHAR(255),
    status            VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    paid_at           TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_trip_payments_trip ON trip_payments (trip_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_trip_payments_session
    ON trip_payments (stripe_session_id)
    WHERE stripe_session_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trip_payments_status ON trip_payments (status);
