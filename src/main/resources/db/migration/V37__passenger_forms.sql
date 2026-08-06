-- =============================================================================
-- V37 — Formulário progressivo de passageiros + vínculo no cofre documental
-- Estende trip_passengers (criada em V36) sem conflitar com operações.
-- =============================================================================

ALTER TABLE trip_passengers
    ADD COLUMN IF NOT EXISTS form_status           VARCHAR(32)  NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN IF NOT EXISTS guardian_passenger_id UUID         REFERENCES trip_passengers (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS whatsapp              VARCHAR(64),
    ADD COLUMN IF NOT EXISTS form_payload          JSONB,
    ADD COLUMN IF NOT EXISTS invite_token          VARCHAR(80),
    ADD COLUMN IF NOT EXISTS invite_sent_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS submitted_at          TIMESTAMPTZ;

-- full_name era NOT NULL no seed operacional; permite slot sem nome definido
ALTER TABLE trip_passengers
    ALTER COLUMN full_name DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_trip_passengers_invite_token
    ON trip_passengers (invite_token)
    WHERE invite_token IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_trip_passengers_guardian
    ON trip_passengers (guardian_passenger_id)
    WHERE guardian_passenger_id IS NOT NULL;

COMMENT ON COLUMN trip_passengers.form_status IS
    'NOT_REQUESTED | INVITED | IN_PROGRESS | SUBMITTED | IN_REVIEW | COMPLETE';
COMMENT ON COLUMN trip_passengers.form_payload IS
    'Respostas parciais/completas do formulário progressivo (JSON)';
COMMENT ON COLUMN trip_passengers.invite_token IS
    'Token do link público do formulário (sem conta obrigatória)';

ALTER TABLE trip_documents
    ADD COLUMN IF NOT EXISTS passenger_id UUID REFERENCES trip_passengers (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS doc_review_status VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_trip_documents_passenger_id
    ON trip_documents (passenger_id)
    WHERE passenger_id IS NOT NULL;

COMMENT ON COLUMN trip_documents.passenger_id IS
    'Passageiro dono do documento de identidade (cofre PII)';
COMMENT ON COLUMN trip_documents.doc_review_status IS
    'NOT_PROVIDED | UPLOADED | IN_REVIEW | VALID | EXPIRING | EXPIRED | REJECTED';
