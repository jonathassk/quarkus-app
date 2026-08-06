-- Tarefas e arquivos comerciais da solicitação/oportunidade B2B.
CREATE TABLE IF NOT EXISTS agency_opportunity_tasks (
    id                UUID PRIMARY KEY,
    opportunity_id    UUID         NOT NULL REFERENCES agency_opportunities (id) ON DELETE CASCADE,
    agency_id         UUID         NOT NULL REFERENCES agencies (id) ON DELETE CASCADE,
    title             VARCHAR(255) NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    due_at            TIMESTAMPTZ,
    assignee_user_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_opp_tasks_opportunity
    ON agency_opportunity_tasks (opportunity_id, status, due_at);

CREATE TABLE IF NOT EXISTS agency_opportunity_files (
    id                UUID PRIMARY KEY,
    opportunity_id    UUID         NOT NULL REFERENCES agency_opportunities (id) ON DELETE CASCADE,
    agency_id         UUID         NOT NULL REFERENCES agencies (id) ON DELETE CASCADE,
    file_name         VARCHAR(255) NOT NULL,
    content_type      VARCHAR(128) NOT NULL,
    size_bytes        BIGINT,
    storage_key       VARCHAR(512) NOT NULL,
    kind              VARCHAR(32)  NOT NULL DEFAULT 'OTHER',
    uploaded_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_opp_files_opportunity
    ON agency_opportunity_files (opportunity_id, created_at DESC);
