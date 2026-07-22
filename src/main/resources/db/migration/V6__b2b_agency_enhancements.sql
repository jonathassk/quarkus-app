-- =============================================================================
-- V6 — B2B agency branding, proposal workflow, tiers, document visibility
-- =============================================================================

-- agencies: white-label + billing
ALTER TABLE agencies ADD COLUMN IF NOT EXISTS whatsapp_number VARCHAR(32);
ALTER TABLE agencies ADD COLUMN IF NOT EXISTS markup_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0;
ALTER TABLE agencies ADD COLUMN IF NOT EXISTS stripe_subscription_id VARCHAR(255);

-- trips: interactive proposal fields
ALTER TABLE trips ADD COLUMN IF NOT EXISTS proposal_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE trips ADD COLUMN IF NOT EXISTS base_cost NUMERIC(12, 2);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS final_price NUMERIC(12, 2);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS share_code VARCHAR(64);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS last_contact_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uk_trips_share_code ON trips (share_code) WHERE share_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trips_proposal_status ON trips (proposal_status);
CREATE INDEX IF NOT EXISTS idx_trips_agency_proposal ON trips (agency_id, proposal_status);

-- proposal tiers (Econômica / Luxo / etc. on the same Magic Link)
CREATE TABLE IF NOT EXISTS trip_proposal_tiers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id     UUID           NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    code        VARCHAR(64)    NOT NULL,
    label       VARCHAR(255)   NOT NULL,
    price_delta NUMERIC(12, 2) NOT NULL DEFAULT 0,
    sort_order  INTEGER        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_trip_proposal_tier_code UNIQUE (trip_id, code)
);

CREATE INDEX IF NOT EXISTS idx_trip_proposal_tiers_trip ON trip_proposal_tiers (trip_id);

-- optional activities selectable on the public proposal
ALTER TABLE activities ADD COLUMN IF NOT EXISTS is_optional BOOLEAN NOT NULL DEFAULT FALSE;

-- vouchers: client-visible vs agency-internal + optional day/activity link
ALTER TABLE trip_documents ADD COLUMN IF NOT EXISTS visibility VARCHAR(32) NOT NULL DEFAULT 'CLIENT';
ALTER TABLE trip_documents ADD COLUMN IF NOT EXISTS activity_id UUID REFERENCES activities(id) ON DELETE SET NULL;
ALTER TABLE trip_documents ADD COLUMN IF NOT EXISTS segment_id UUID REFERENCES trip_segments(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_trip_documents_visibility ON trip_documents (trip_id, visibility);
