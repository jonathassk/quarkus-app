-- Workspace de operação e reservas (MVP): passageiros, fornecedores, serviços operacionais e prazos.
-- Status geral da viagem passa a refletir o ciclo operacional pós-venda.

-- ── Migrar operation_status legado ──────────────────────────────────────────
UPDATE trips SET operation_status = 'PREPARING_RESERVATIONS' WHERE operation_status = 'TO_RESERVE';
UPDATE trips SET operation_status = 'RESERVATIONS_IN_PROGRESS' WHERE operation_status = 'REQUESTED';
UPDATE trips SET operation_status = 'PARTIALLY_CONFIRMED' WHERE operation_status = 'RESERVED';
UPDATE trips SET operation_status = 'READY_TO_TRAVEL' WHERE operation_status = 'ISSUED';
-- CANCELLED permanece

-- ── Fornecedores (cadastro básico da agência) ───────────────────────────────
CREATE TABLE agency_suppliers (
    id                  UUID PRIMARY KEY,
    agency_id           UUID NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,
    category            VARCHAR(64) NOT NULL DEFAULT 'OTHER',
    contact_name        VARCHAR(255),
    email               VARCHAR(255),
    whatsapp            VARCHAR(64),
    website             VARCHAR(512),
    currencies          VARCHAR(64),
    notes               TEXT,
    default_policy      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agency_suppliers_agency ON agency_suppliers(agency_id);
CREATE INDEX idx_agency_suppliers_agency_name ON agency_suppliers(agency_id, name);

-- ── Passageiros da viagem ───────────────────────────────────────────────────
CREATE TABLE trip_passengers (
    id                      UUID PRIMARY KEY,
    trip_id                 UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    agency_client_id        UUID REFERENCES agency_clients(id) ON DELETE SET NULL,
    full_name               VARCHAR(255) NOT NULL,
    email                   VARCHAR(255),
    phone                   VARCHAR(64),
    document_type           VARCHAR(32),
    document_number         VARCHAR(64),
    document_expires_at     DATE,
    nationality             VARCHAR(128),
    birth_date              DATE,
    passenger_type          VARCHAR(16) NOT NULL DEFAULT 'ADULT',
    is_lead                 BOOLEAN NOT NULL DEFAULT FALSE,
    doc_checklist_json      JSONB,
    notes                   TEXT,
    sort_order              INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trip_passengers_trip ON trip_passengers(trip_id);

-- ── Serviços operacionais (1 por item aprovado) ─────────────────────────────
CREATE TABLE operational_services (
    id                      UUID PRIMARY KEY,
    trip_id                 UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    proposal_item_id        UUID REFERENCES proposal_items(id) ON DELETE SET NULL,
    supplier_id             UUID REFERENCES agency_suppliers(id) ON DELETE SET NULL,
    service_type            VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    name                    VARCHAR(255) NOT NULL,
    subtitle                VARCHAR(512),
    status                  VARCHAR(32) NOT NULL DEFAULT 'TO_RESERVE',
    next_action             VARCHAR(64),
    next_action_label       VARCHAR(255),
    next_action_due_at      TIMESTAMPTZ,
    details_json            JSONB,
    public_info_json        JSONB,
    internal_notes          TEXT,
    supplier_name           VARCHAR(255),
    cost_estimated_minor    BIGINT,
    price_approved_minor    BIGINT,
    currency                VARCHAR(3),
    confirmed_cost_minor    BIGINT,
    cost_divergence_minor   BIGINT,
    locator                 VARCHAR(128),
    ticket_number           VARCHAR(128),
    confirmed_at            TIMESTAMPTZ,
    cancellation_policy     TEXT,
    published               BOOLEAN NOT NULL DEFAULT FALSE,
    cancel_reason           TEXT,
    cancelled_at            TIMESTAMPTZ,
    cancelled_by_user_id    UUID REFERENCES users(id) ON DELETE SET NULL,
    estimated_penalty_minor BIGINT,
    supplier_credit_minor   BIGINT,
    sort_order              INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_operational_services_trip ON operational_services(trip_id);
CREATE INDEX idx_operational_services_status ON operational_services(trip_id, status);
CREATE UNIQUE INDEX uk_operational_services_proposal_item
    ON operational_services(proposal_item_id) WHERE proposal_item_id IS NOT NULL;

-- ── Vínculo serviço ↔ passageiro ────────────────────────────────────────────
CREATE TABLE operational_service_passengers (
    service_id      UUID NOT NULL REFERENCES operational_services(id) ON DELETE CASCADE,
    passenger_id    UUID NOT NULL REFERENCES trip_passengers(id) ON DELETE CASCADE,
    PRIMARY KEY (service_id, passenger_id)
);

-- ── Prazos operacionais ─────────────────────────────────────────────────────
CREATE TABLE operational_deadlines (
    id              UUID PRIMARY KEY,
    trip_id         UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    service_id      UUID REFERENCES operational_services(id) ON DELETE CASCADE,
    deadline_type   VARCHAR(64) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    due_at          TIMESTAMPTZ NOT NULL,
    alert_level     VARCHAR(16) NOT NULL DEFAULT 'INFO',
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_operational_deadlines_trip ON operational_deadlines(trip_id, due_at);
CREATE INDEX idx_operational_deadlines_open
    ON operational_deadlines(trip_id, due_at) WHERE completed_at IS NULL;

-- ── Documentos operacionais (extensão de trip_documents) ────────────────────
ALTER TABLE trip_documents
    ADD COLUMN IF NOT EXISTS operational_service_id UUID REFERENCES operational_services(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS document_kind VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN IF NOT EXISTS operational_doc_status VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_trip_documents_ops_service
    ON trip_documents(operational_service_id)
    WHERE operational_service_id IS NOT NULL;

-- ── Alterações pós-aprovação ────────────────────────────────────────────────
CREATE TABLE service_change_requests (
    id                      UUID PRIMARY KEY,
    trip_id                 UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    service_id              UUID NOT NULL REFERENCES operational_services(id) ON DELETE CASCADE,
    status                  VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    request_note            TEXT,
    price_delta_minor       BIGINT,
    requested_by_user_id    UUID REFERENCES users(id) ON DELETE SET NULL,
    resolved_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_service_change_requests_trip ON service_change_requests(trip_id);
CREATE INDEX idx_service_change_requests_service ON service_change_requests(service_id);
