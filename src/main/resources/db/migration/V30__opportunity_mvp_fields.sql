-- Campos comerciais do MVP de oportunidades
ALTER TABLE agency_opportunities
    ADD COLUMN IF NOT EXISTS priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS estimated_value NUMERIC(14, 2),
    ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS next_action_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS next_action_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS next_action_note TEXT,
    ADD COLUMN IF NOT EXISTS next_action_assignee_id UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS lost_reason_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS lost_competitor TEXT,
    ADD COLUMN IF NOT EXISTS lost_note TEXT,
    ADD COLUMN IF NOT EXISTS lost_may_reactivate BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS lost_reactivate_at DATE;

-- Backfill: usar follow-up legado e orçamento como estimativa
UPDATE agency_opportunities
SET last_activity_at = COALESCE(updated_at, created_at)
WHERE last_activity_at IS NULL;

UPDATE agency_opportunities
SET next_action_at = next_follow_up_at
WHERE next_action_at IS NULL
  AND next_follow_up_at IS NOT NULL;

UPDATE agency_opportunities
SET estimated_value = COALESCE(budget_max, budget_min)
WHERE estimated_value IS NULL
  AND (budget_max IS NOT NULL OR budget_min IS NOT NULL);

UPDATE agency_opportunities
SET next_action_type = 'FOLLOW_UP'
WHERE next_action_type IS NULL
  AND next_action_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_agency_opportunities_next_action
    ON agency_opportunities (agency_id, next_action_at);

CREATE INDEX IF NOT EXISTS idx_agency_opportunities_priority
    ON agency_opportunities (agency_id, priority);

-- Timeline / atividades da oportunidade
CREATE TABLE IF NOT EXISTS agency_opportunity_activities (
    id               UUID PRIMARY KEY,
    opportunity_id   UUID NOT NULL REFERENCES agency_opportunities (id) ON DELETE CASCADE,
    agency_id        UUID NOT NULL REFERENCES agencies (id) ON DELETE CASCADE,
    actor_user_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    actor_label      VARCHAR(255),
    activity_type    VARCHAR(64) NOT NULL,
    title            VARCHAR(255) NOT NULL,
    body             TEXT,
    metadata_json    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_opp_activities_opportunity
    ON agency_opportunity_activities (opportunity_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_opp_activities_agency
    ON agency_opportunity_activities (agency_id, created_at DESC);
