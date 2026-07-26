-- =============================================================================
-- V15 — Refino IA (épico 6) + templates de roteiro (épico 7)
-- =============================================================================

CREATE TABLE IF NOT EXISTS trip_segment_revisions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id      UUID           NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    segment_id   UUID           NOT NULL REFERENCES trip_segments(id) ON DELETE CASCADE,
    payload      JSONB          NOT NULL,
    reason       VARCHAR(64),
    created_by   UUID           REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_trip_segment_revisions_segment
    ON trip_segment_revisions (segment_id, created_at DESC);

CREATE TABLE IF NOT EXISTS trip_templates (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope        VARCHAR(32)    NOT NULL,
    kind         VARCHAR(32)    NOT NULL DEFAULT 'FULL_TRIP',
    owner_id     UUID           REFERENCES users(id) ON DELETE CASCADE,
    agency_id    UUID           REFERENCES agencies(id) ON DELETE CASCADE,
    name         VARCHAR(255)   NOT NULL,
    description  TEXT,
    payload      JSONB          NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_trip_templates_owner CHECK (
        (scope = 'PERSONAL' AND owner_id IS NOT NULL)
        OR (scope = 'AGENCY' AND agency_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_trip_templates_owner ON trip_templates (owner_id) WHERE scope = 'PERSONAL';
CREATE INDEX IF NOT EXISTS idx_trip_templates_agency ON trip_templates (agency_id) WHERE scope = 'AGENCY';
