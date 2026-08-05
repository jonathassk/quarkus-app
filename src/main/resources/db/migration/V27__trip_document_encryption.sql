-- =============================================================================
-- V27 — Criptografia de documentos de viagem (AES-GCM no app antes do R2)
-- encryption_version: 0 = legado (plaintext no R2), 1 = AES-256-GCM
-- =============================================================================

ALTER TABLE trip_documents
    ADD COLUMN IF NOT EXISTS encryption_version SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN trip_documents.encryption_version IS
    '0 = plaintext legado; 1 = AES-256-GCM (app) antes do armazenamento no R2';
