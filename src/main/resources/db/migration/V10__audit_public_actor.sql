-- =============================================================================
-- V10 — Auditoria B2B com ator externo (cliente via link público)
-- =============================================================================
-- Ações feitas pelo cliente na proposta pública não têm usuário autenticado.
-- Sem ator, o log passava a apontar para o consultor criador da viagem.

ALTER TABLE b2b_trip_logs ALTER COLUMN actor_user_id DROP NOT NULL;
ALTER TABLE b2b_trip_logs ADD COLUMN IF NOT EXISTS actor_label VARCHAR(255);
