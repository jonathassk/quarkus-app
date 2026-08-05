-- Contato único com estados (Prospect / Cliente / Inativo).
ALTER TABLE agency_clients
    ADD COLUMN IF NOT EXISTS contact_status VARCHAR(32) NOT NULL DEFAULT 'PROSPECT';

UPDATE agency_clients
SET contact_status = 'CLIENT'
WHERE contact_status = 'PROSPECT'
  AND EXISTS (
      SELECT 1 FROM trips t
      WHERE t.client_id = agency_clients.id
        AND t.proposal_status IN (
            'APPROVED', 'PENDING_PAYMENT', 'CONFIRMED', 'IN_TRIP', 'COMPLETED'
        )
  );

-- Solicitação / oportunidade comercial (pré-proposta e ciclo de venda).
CREATE TABLE IF NOT EXISTS agency_opportunities (
    id                      UUID PRIMARY KEY,
    agency_id               UUID NOT NULL REFERENCES agencies (id) ON DELETE CASCADE,
    client_id               UUID NOT NULL REFERENCES agency_clients (id) ON DELETE CASCADE,
    trip_id                 UUID REFERENCES trips (id) ON DELETE SET NULL,
    title                   VARCHAR(255) NOT NULL,
    stage                   VARCHAR(32)  NOT NULL DEFAULT 'NEW',
    request_summary         TEXT,
    assigned_consultant_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    next_follow_up_at       TIMESTAMPTZ,
    lead_source             VARCHAR(64)  NOT NULL DEFAULT 'OTHER',
    lead_source_detail      TEXT,
    preferred_channel       VARCHAR(32),
    best_contact_time       VARCHAR(128),
    city                    VARCHAR(128),
    country                 VARCHAR(128),
    is_passenger            BOOLEAN,
    decision_makers         TEXT,
    origin_city             VARCHAR(128),
    destinations            TEXT,
    start_date              DATE,
    end_date                DATE,
    duration_days           INTEGER,
    dates_flexible          BOOLEAN      NOT NULL DEFAULT FALSE,
    alternate_airports      TEXT,
    trip_type               VARCHAR(64),
    adults                  INTEGER,
    children_count          INTEGER,
    children_ages           TEXT,
    infants                 INTEGER,
    rooms                   INTEGER,
    occupancy_preference    VARCHAR(64),
    passengers_estimated    BOOLEAN      NOT NULL DEFAULT TRUE,
    desired_services        TEXT,
    budget_min              NUMERIC(14, 2),
    budget_max              NUMERIC(14, 2),
    budget_currency         VARCHAR(8)   DEFAULT 'BRL',
    budget_per_person       BOOLEAN,
    budget_includes_flights BOOLEAN,
    payment_preference      TEXT,
    accepts_installments    BOOLEAN,
    budget_estimated_by_agent BOOLEAN,
    preferences             TEXT,
    restrictions            TEXT,
    decision_deadline       DATE,
    urgency                 VARCHAR(32),
    has_other_proposals     BOOLEAN,
    has_existing_reservation BOOLEAN,
    decision_maker          TEXT,
    main_criterion          VARCHAR(64),
    qualification_status    VARCHAR(32)  NOT NULL DEFAULT 'INSUFFICIENT',
    ready_to_quote_override BOOLEAN      NOT NULL DEFAULT FALSE,
    lost_reason             TEXT,
    lost_at                 TIMESTAMPTZ,
    won_at                  TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agency_opportunities_agency_stage
    ON agency_opportunities (agency_id, stage);

CREATE INDEX IF NOT EXISTS idx_agency_opportunities_client
    ON agency_opportunities (client_id);

CREATE INDEX IF NOT EXISTS idx_agency_opportunities_consultant
    ON agency_opportunities (assigned_consultant_id);

CREATE INDEX IF NOT EXISTS idx_agency_opportunities_follow_up
    ON agency_opportunities (agency_id, next_follow_up_at);
