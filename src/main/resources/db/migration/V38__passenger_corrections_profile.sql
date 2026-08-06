-- =============================================================================
-- V38 — Fase 2: correções por campo, consentimento de perfil, alertas
-- =============================================================================

CREATE TABLE IF NOT EXISTS passenger_field_corrections (
    id                    UUID         PRIMARY KEY,
    trip_passenger_id     UUID         NOT NULL REFERENCES trip_passengers (id) ON DELETE CASCADE,
    trip_id               UUID         NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    field_name            VARCHAR(64)  NOT NULL,
    old_value             TEXT,
    expected_value        TEXT,
    corrected_value       TEXT,
    agent_note            TEXT,
    status                VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    requested_by_user_id  UUID         REFERENCES users (id) ON DELETE SET NULL,
    resolved_by_label     VARCHAR(255),
    requested_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pax_corrections_passenger
    ON passenger_field_corrections (trip_passenger_id, status);
CREATE INDEX IF NOT EXISTS idx_pax_corrections_trip
    ON passenger_field_corrections (trip_id, status);

COMMENT ON TABLE passenger_field_corrections IS
    'Solicitações de correção campo a campo (agente → passageiro), com auditoria old/new';

ALTER TABLE agency_clients
    ADD COLUMN IF NOT EXISTS traveler_reuse_consent_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS traveler_prefs_json JSONB;

COMMENT ON COLUMN agency_clients.traveler_reuse_consent_at IS
    'Consentimento do passageiro para reutilizar dados em viagens futuras nesta agência';
COMMENT ON COLUMN agency_clients.traveler_prefs_json IS
    'Preferências/necessidades autorizadas para reuso (escopo da agência)';
