-- =============================================================================
-- V16 — Trial de assinatura (5 dias): anti-abuse por usuário
-- =============================================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS trial_used_at TIMESTAMPTZ;

COMMENT ON COLUMN users.trial_used_at IS
    'Quando o usuário iniciou o trial de 5 dias (Stripe). NULL = ainda elegível.';
