-- Execute no Neon SQL Editor se trip_documents ainda não existir.
-- Espelha db/migration/V1__baseline_uuid_schema.sql (seção trip_documents).
--
-- ATENÇÃO: schema migrado para UUID (UUIDv7 gerado na aplicação). A PK e as FKs
-- para trips/users são UUID. Pressupõe que `trips` e `users` já existam em UUID.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS trip_documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id      UUID         NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    title        VARCHAR(255) NOT NULL,
    s3_key       VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    uploaded_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trip_documents_trip_id ON trip_documents(trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_documents_status ON trip_documents(status);
