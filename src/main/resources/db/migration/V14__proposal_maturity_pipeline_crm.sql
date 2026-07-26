-- =============================================================================
-- V14 — Proposta B2B madura (épico 4) + pipeline/CRM (épico 5)
-- =============================================================================

-- Épico 4: validade, motivo de recusa, aceite digital
ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS proposal_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS proposal_reject_reason TEXT;

CREATE TABLE IF NOT EXISTS proposal_acceptances (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id      UUID           NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    name         VARCHAR(255)   NOT NULL,
    email        VARCHAR(255)   NOT NULL,
    ip           VARCHAR(64),
    user_agent   VARCHAR(512),
    accepted_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    tier_codes   VARCHAR(512),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_proposal_acceptances_trip
    ON proposal_acceptances (trip_id);

-- Épico 5: CRM leve
CREATE TABLE IF NOT EXISTS agency_clients (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id    UUID           NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
    name         VARCHAR(255)   NOT NULL,
    email        VARCHAR(255)   NOT NULL,
    phone        VARCHAR(64),
    notes        TEXT,
    tags         TEXT,
    user_id      UUID           REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agency_clients_email
    ON agency_clients (agency_id, lower(email));
CREATE INDEX IF NOT EXISTS idx_agency_clients_agency
    ON agency_clients (agency_id);

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS client_id UUID REFERENCES agency_clients(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS assigned_consultant_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_trips_client ON trips (client_id);
CREATE INDEX IF NOT EXISTS idx_trips_assigned_consultant ON trips (assigned_consultant_id);
CREATE INDEX IF NOT EXISTS idx_trips_agency_proposal_status
    ON trips (agency_id, proposal_status);

-- Épico 5.3: convite de membro com pendência
CREATE TABLE IF NOT EXISTS agency_invites (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id    UUID           NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
    email        VARCHAR(255)   NOT NULL,
    agency_role  VARCHAR(50)    NOT NULL DEFAULT 'AGENCY_CONSULTANT',
    token        VARCHAR(64)    NOT NULL,
    status       VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    invited_by   UUID           REFERENCES users(id) ON DELETE SET NULL,
    expires_at   TIMESTAMPTZ    NOT NULL,
    accepted_at  TIMESTAMPTZ,
    accepted_user_id UUID       REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agency_invites_token ON agency_invites (token);
CREATE INDEX IF NOT EXISTS idx_agency_invites_agency ON agency_invites (agency_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agency_invites_pending_email
    ON agency_invites (agency_id, lower(email)) WHERE status = 'PENDING';
