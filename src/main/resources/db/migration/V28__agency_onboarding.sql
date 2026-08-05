-- Onboarding B2B: estado do wizard, branding estendido, demo e follow-up

ALTER TABLE agencies
    ADD COLUMN IF NOT EXISTS onboarding_step VARCHAR(32) NOT NULL DEFAULT 'WELCOME',
    ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS onboarding_skipped_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS onboarding_trip_id UUID,
    ADD COLUMN IF NOT EXISTS onboarding_client_id UUID,
    ADD COLUMN IF NOT EXISTS demo_data_active BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS agent_title VARCHAR(128),
    ADD COLUMN IF NOT EXISTS agent_photo_url VARCHAR(512),
    ADD COLUMN IF NOT EXISTS website_or_instagram VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pricing_model VARCHAR(32);

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS is_demo BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS next_follow_up_at TIMESTAMPTZ;

ALTER TABLE agency_clients
    ADD COLUMN IF NOT EXISTS is_demo BOOLEAN NOT NULL DEFAULT FALSE;
