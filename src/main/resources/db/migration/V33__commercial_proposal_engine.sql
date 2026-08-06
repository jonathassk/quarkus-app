-- Motor comercial multiopção: Proposal → Version → Options (Trip) + Items + AddOns + Adjustments.
-- Valores monetários em minor units (BIGINT). Trip legado permanece NUMERIC.

CREATE TABLE agency_proposals (
    id                          UUID PRIMARY KEY,
    agency_id                   UUID NOT NULL REFERENCES agencies(id),
    opportunity_id              UUID REFERENCES agency_opportunities(id),
    client_id                   UUID REFERENCES agency_clients(id),
    consultant_id               UUID REFERENCES users(id),
    share_code                  VARCHAR(64) UNIQUE,
    presentation_currency       VARCHAR(3) NOT NULL DEFAULT 'BRL',
    price_visibility            VARCHAR(32) NOT NULL DEFAULT 'TOTAL_ONLY',
    format                      VARCHAR(16) NOT NULL DEFAULT 'SINGLE',
    current_version_id          UUID,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agency_proposals_agency ON agency_proposals(agency_id);
CREATE INDEX idx_agency_proposals_opportunity ON agency_proposals(opportunity_id);
CREATE INDEX idx_agency_proposals_share_code ON agency_proposals(share_code);

CREATE TABLE proposal_versions (
    id                          UUID PRIMARY KEY,
    proposal_id                 UUID NOT NULL REFERENCES agency_proposals(id) ON DELETE CASCADE,
    version_number              INT NOT NULL,
    status                      VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    pricing_edit_mode           VARCHAR(16) NOT NULL DEFAULT 'QUICK',
    expires_at                  TIMESTAMPTZ,
    client_email                VARCHAR(255),
    client_name                 VARCHAR(255),
    allow_negotiation           BOOLEAN NOT NULL DEFAULT FALSE,
    recommendation_note         TEXT,
    sent_at                     TIMESTAMPTZ,
    last_viewed_at              TIMESTAMPTZ,
    view_count                  INT NOT NULL DEFAULT 0,
    reject_reason               TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_proposal_version UNIQUE (proposal_id, version_number)
);

CREATE INDEX idx_proposal_versions_proposal ON proposal_versions(proposal_id);
CREATE INDEX idx_proposal_versions_status ON proposal_versions(status);

ALTER TABLE agency_proposals
    ADD CONSTRAINT fk_agency_proposals_current_version
    FOREIGN KEY (current_version_id) REFERENCES proposal_versions(id);

CREATE TABLE proposal_options (
    id                          UUID PRIMARY KEY,
    version_id                  UUID NOT NULL REFERENCES proposal_versions(id) ON DELETE CASCADE,
    trip_id                     UUID NOT NULL REFERENCES trips(id),
    position_code               VARCHAR(32) NOT NULL DEFAULT 'RECOMMENDED',
    sort_order                  INT NOT NULL DEFAULT 0,
    recommended                 BOOLEAN NOT NULL DEFAULT FALSE,
    hidden                      BOOLEAN NOT NULL DEFAULT FALSE,
    name                        VARCHAR(255) NOT NULL,
    subtitle                    VARCHAR(512),
    short_description           TEXT,
    cover_image_url             VARCHAR(512),
    includes_json               JSONB,
    excludes_json               JSONB,
    payment_conditions          TEXT,
    -- Totais denormalizados (minor units) na moeda de apresentação
    supplier_cost_minor         BIGINT NOT NULL DEFAULT 0,
    markup_amount_minor         BIGINT NOT NULL DEFAULT 0,
    service_fee_minor           BIGINT NOT NULL DEFAULT 0,
    commission_minor            BIGINT NOT NULL DEFAULT 0,
    client_price_minor          BIGINT NOT NULL DEFAULT 0,
    expected_revenue_minor      BIGINT NOT NULL DEFAULT 0,
    margin_bps                  INT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_proposal_option_trip UNIQUE (version_id, trip_id)
);

CREATE INDEX idx_proposal_options_version ON proposal_options(version_id);
CREATE INDEX idx_proposal_options_trip ON proposal_options(trip_id);

CREATE TABLE proposal_items (
    id                          UUID PRIMARY KEY,
    version_id                  UUID NOT NULL REFERENCES proposal_versions(id) ON DELETE CASCADE,
    option_id                   UUID REFERENCES proposal_options(id) ON DELETE CASCADE,
    scope                       VARCHAR(16) NOT NULL DEFAULT 'OPTION',
    item_type                   VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    name                        VARCHAR(255) NOT NULL,
    subtitle                    VARCHAR(512),
    details_json                JSONB,
    pricing_mode                VARCHAR(16) NOT NULL DEFAULT 'COST_PLUS',
    -- Custo original (pode ser outra moeda)
    cost_currency               VARCHAR(3),
    cost_amount_minor           BIGINT,
    fx_rate_micros              BIGINT,
    fx_date                     DATE,
    fx_source                   VARCHAR(64),
    fx_protection_bps           INT,
    -- Convertido / operacional na moeda da proposta
    cost_minor                  BIGINT,
    markup_kind                 VARCHAR(16),
    markup_value_minor          BIGINT,
    markup_percent_bps          INT,
    supplier_public_price_minor BIGINT,
    commission_kind             VARCHAR(16),
    commission_value_minor      BIGINT,
    commission_percent_bps      INT,
    service_fee_minor           BIGINT NOT NULL DEFAULT 0,
    client_price_minor          BIGINT,
    expected_commission_minor   BIGINT,
    expected_revenue_minor      BIGINT,
    supplier_name               VARCHAR(255),
    supplier_visibility         VARCHAR(32) NOT NULL DEFAULT 'SHOW_NAME',
    optional_flag               BOOLEAN NOT NULL DEFAULT FALSE,
    hide_price                  BOOLEAN NOT NULL DEFAULT FALSE,
    quote_expires_at            TIMESTAMPTZ,
    sort_order                  INT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_proposal_item_scope CHECK (
        (scope = 'COMMON' AND option_id IS NULL) OR
        (scope = 'OPTION' AND option_id IS NOT NULL)
    )
);

CREATE INDEX idx_proposal_items_version ON proposal_items(version_id);
CREATE INDEX idx_proposal_items_option ON proposal_items(option_id);

CREATE TABLE proposal_addons (
    id                          UUID PRIMARY KEY,
    version_id                  UUID NOT NULL REFERENCES proposal_versions(id) ON DELETE CASCADE,
    name                        VARCHAR(255) NOT NULL,
    description                 TEXT,
    price_minor                 BIGINT NOT NULL DEFAULT 0,
    pricing_unit                VARCHAR(16) NOT NULL DEFAULT 'TOTAL',
    quantity_default            INT NOT NULL DEFAULT 1,
    eligible_option_ids         JSONB,
    required_flag               BOOLEAN NOT NULL DEFAULT FALSE,
    optional_flag               BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at                  TIMESTAMPTZ,
    sort_order                  INT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_proposal_addons_version ON proposal_addons(version_id);

CREATE TABLE proposal_adjustments (
    id                          UUID PRIMARY KEY,
    version_id                  UUID NOT NULL REFERENCES proposal_versions(id) ON DELETE CASCADE,
    option_id                   UUID REFERENCES proposal_options(id) ON DELETE CASCADE,
    adjustment_type             VARCHAR(32) NOT NULL,
    amount_minor                BIGINT NOT NULL,
    percent_bps                 INT,
    reason                      TEXT NOT NULL,
    previous_client_price_minor BIGINT,
    created_by                  UUID REFERENCES users(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_proposal_adjustments_version ON proposal_adjustments(version_id);

ALTER TABLE agency_opportunities
    ADD COLUMN IF NOT EXISTS proposal_id UUID REFERENCES agency_proposals(id);

CREATE INDEX IF NOT EXISTS idx_agency_opportunities_proposal ON agency_opportunities(proposal_id);

ALTER TABLE proposal_acceptances
    ADD COLUMN IF NOT EXISTS proposal_id UUID REFERENCES agency_proposals(id),
    ADD COLUMN IF NOT EXISTS version_id UUID REFERENCES proposal_versions(id),
    ADD COLUMN IF NOT EXISTS option_id UUID REFERENCES proposal_options(id),
    ADD COLUMN IF NOT EXISTS addon_ids JSONB,
    ADD COLUMN IF NOT EXISTS total_minor BIGINT,
    ADD COLUMN IF NOT EXISTS terms_text TEXT,
    ADD COLUMN IF NOT EXISTS session_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_proposal_acceptances_proposal ON proposal_acceptances(proposal_id);

-- Backfill: cada Trip B2B com share/pricing vira Proposal + Version + 1 Option
INSERT INTO agency_proposals (
    id, agency_id, opportunity_id, client_id, consultant_id, share_code,
    presentation_currency, price_visibility, format, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    t.agency_id,
    o.id,
    t.client_id,
    t.assigned_consultant_id,
    t.share_code,
    COALESCE(NULLIF(t.currency, ''), 'BRL'),
    'TOTAL_ONLY',
    'SINGLE',
    COALESCE(t.created_at, NOW()),
    COALESCE(t.updated_at, NOW())
FROM trips t
LEFT JOIN agency_opportunities o ON o.trip_id = t.id
WHERE t.agency_id IS NOT NULL
  AND t.share_code IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM agency_proposals p WHERE p.share_code = t.share_code
  );

INSERT INTO proposal_versions (
    id, proposal_id, version_number, status, pricing_edit_mode,
    expires_at, client_email, client_name, allow_negotiation,
    sent_at, last_viewed_at, view_count, reject_reason, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    p.id,
    1,
    CASE
        WHEN t.proposal_status IN ('DRAFT', 'QUOTING') THEN 'DRAFT'
        WHEN t.proposal_status = 'SENT' THEN 'SENT'
        WHEN t.proposal_status = 'NEGOTIATING' THEN 'SENT'
        WHEN t.proposal_status IN ('APPROVED', 'PENDING_PAYMENT', 'CONFIRMED', 'IN_TRIP', 'COMPLETED') THEN 'APPROVED'
        WHEN t.proposal_status = 'REJECTED' THEN 'REJECTED'
        ELSE 'DRAFT'
    END,
    'QUICK',
    t.proposal_expires_at,
    t.proposal_client_email,
    t.proposal_client_name,
    COALESCE(t.allow_negotiation, FALSE),
    t.proposal_sent_at,
    t.proposal_last_viewed_at,
    COALESCE(t.proposal_view_count, 0),
    t.proposal_reject_reason,
    COALESCE(t.created_at, NOW()),
    COALESCE(t.updated_at, NOW())
FROM agency_proposals p
JOIN trips t ON t.share_code = p.share_code AND t.agency_id = p.agency_id
WHERE NOT EXISTS (
    SELECT 1 FROM proposal_versions v WHERE v.proposal_id = p.id
);

UPDATE agency_proposals p
SET current_version_id = v.id
FROM proposal_versions v
WHERE v.proposal_id = p.id AND v.version_number = 1 AND p.current_version_id IS NULL;

INSERT INTO proposal_options (
    id, version_id, trip_id, position_code, sort_order, recommended, hidden,
    name, subtitle, short_description, cover_image_url,
    supplier_cost_minor, client_price_minor, expected_revenue_minor, margin_bps,
    created_at, updated_at
)
SELECT
    gen_random_uuid(),
    v.id,
    t.id,
    'RECOMMENDED',
    0,
    TRUE,
    FALSE,
    COALESCE(t.name, 'Opção'),
    NULL,
    LEFT(t.description, 500),
    t.cover_image_url,
    COALESCE(ROUND(t.base_cost * 100), 0)::BIGINT,
    COALESCE(ROUND(t.final_price * 100), 0)::BIGINT,
    CASE
        WHEN t.final_price IS NOT NULL AND t.base_cost IS NOT NULL
            THEN COALESCE(ROUND((t.final_price - t.base_cost) * 100), 0)::BIGINT
        ELSE 0
    END,
    CASE
        WHEN t.final_price IS NOT NULL AND t.final_price > 0 AND t.base_cost IS NOT NULL
            THEN ROUND(((t.final_price - t.base_cost) / t.final_price) * 10000)::INT
        ELSE NULL
    END,
    COALESCE(t.created_at, NOW()),
    COALESCE(t.updated_at, NOW())
FROM proposal_versions v
JOIN agency_proposals p ON p.id = v.proposal_id
JOIN trips t ON t.share_code = p.share_code AND t.agency_id = p.agency_id
WHERE NOT EXISTS (
    SELECT 1 FROM proposal_options o WHERE o.version_id = v.id
);

-- Item PACKAGE sintético a partir do pricing legado
INSERT INTO proposal_items (
    id, version_id, option_id, scope, item_type, name, pricing_mode,
    cost_minor, client_price_minor, expected_revenue_minor, service_fee_minor,
    markup_kind, markup_percent_bps, sort_order
)
SELECT
    gen_random_uuid(),
    o.version_id,
    o.id,
    'OPTION',
    'PACKAGE',
    'Pacote',
    'COST_PLUS',
    o.supplier_cost_minor,
    o.client_price_minor,
    o.expected_revenue_minor,
    0,
    'PERCENT',
    CASE
        WHEN o.supplier_cost_minor > 0 AND o.client_price_minor > o.supplier_cost_minor
            THEN ROUND(((o.client_price_minor - o.supplier_cost_minor)::NUMERIC / o.supplier_cost_minor) * 10000)::INT
        ELSE NULL
    END,
    0
FROM proposal_options o
WHERE o.client_price_minor > 0 OR o.supplier_cost_minor > 0;

-- Tiers legados → add-ons
INSERT INTO proposal_addons (
    id, version_id, name, description, price_minor, pricing_unit,
    quantity_default, optional_flag, required_flag, sort_order
)
SELECT
    gen_random_uuid(),
    o.version_id,
    COALESCE(tier.label, tier.code),
    'Tier legado migrado',
    COALESCE(ROUND(tier.price_delta * 100), 0)::BIGINT,
    'TOTAL',
    1,
    TRUE,
    FALSE,
    COALESCE(tier.sort_order, 0)
FROM proposal_options o
JOIN trip_proposal_tiers tier ON tier.trip_id = o.trip_id
WHERE COALESCE(tier.price_delta, 0) <> 0;

UPDATE agency_opportunities o
SET proposal_id = p.id
FROM agency_proposals p
WHERE p.opportunity_id = o.id AND o.proposal_id IS NULL;

UPDATE agency_opportunities o
SET proposal_id = p.id
FROM trips t
JOIN agency_proposals p ON p.share_code = t.share_code
WHERE o.trip_id = t.id AND o.proposal_id IS NULL;

UPDATE proposal_acceptances a
SET proposal_id = p.id,
    version_id = p.current_version_id,
    option_id = (
        SELECT o.id FROM proposal_options o
        WHERE o.version_id = p.current_version_id AND o.trip_id = a.trip_id
        LIMIT 1
    ),
    total_minor = COALESCE(ROUND(t.final_price * 100), 0)::BIGINT
FROM trips t
JOIN agency_proposals p ON p.share_code = t.share_code
WHERE a.trip_id = t.id AND a.proposal_id IS NULL;
