-- V26 — Pipeline com mais etapas + operação + arquivo (histórico)

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS allow_negotiation BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS operation_status VARCHAR(32);

-- Aprovadas sem pagamento entram em Confirmada (operação a reservar).
UPDATE trips
SET proposal_status = 'CONFIRMED',
    operation_status = COALESCE(operation_status, 'TO_RESERVE')
WHERE proposal_status = 'APPROVED';

UPDATE trips
SET operation_status = 'TO_RESERVE'
WHERE proposal_status = 'CONFIRMED'
  AND (operation_status IS NULL OR operation_status = '');

CREATE INDEX IF NOT EXISTS idx_trips_agency_proposal_status_updated
    ON trips (agency_id, proposal_status, updated_at DESC);
