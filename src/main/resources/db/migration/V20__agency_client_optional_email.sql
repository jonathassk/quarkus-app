-- =============================================================================
-- V20 — Agency clients: e-mail opcional (visitantes sem conta/e-mail no site)
-- =============================================================================

ALTER TABLE agency_clients
    ALTER COLUMN email DROP NOT NULL;

DROP INDEX IF EXISTS uk_agency_clients_email;

-- Unicidade só quando há e-mail preenchido (permite vários visitantes sem e-mail).
CREATE UNIQUE INDEX IF NOT EXISTS uk_agency_clients_email
    ON agency_clients (agency_id, lower(email))
    WHERE email IS NOT NULL AND btrim(email) <> '';
