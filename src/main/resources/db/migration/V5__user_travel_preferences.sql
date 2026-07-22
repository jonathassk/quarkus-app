-- =============================================================================
-- V5 — Preferências de viagem do usuário (moeda, dieta, logística)
-- =============================================================================

CREATE TABLE IF NOT EXISTS user_travel_preferences (
    user_id                      UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    currency                     VARCHAR(3)  NOT NULL DEFAULT 'BRL',
    unit_system                  VARCHAR(16) NOT NULL DEFAULT 'metric',
    seat_preference              VARCHAR(16) NOT NULL DEFAULT 'none',
    accommodation_preference     VARCHAR(16) NOT NULL DEFAULT 'hotel',
    dietary_restrictions         TEXT        NOT NULL DEFAULT '',
    base_airport                 VARCHAR(3)  NOT NULL DEFAULT '',
    cloud_backup_enabled         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now()
);
