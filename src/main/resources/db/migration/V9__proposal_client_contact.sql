-- =============================================================================
-- V9 — Contato do cliente na proposta (destinatário do e-mail de envio)
-- =============================================================================
-- Guardar o contato permite reenviar a proposta sem redigitar o e-mail.

ALTER TABLE trips ADD COLUMN IF NOT EXISTS proposal_client_email VARCHAR(255);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS proposal_client_name VARCHAR(255);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS proposal_sent_at TIMESTAMPTZ;
