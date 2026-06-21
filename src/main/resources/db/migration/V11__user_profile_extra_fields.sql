-- =============================================================================
-- V11: Campos extras de perfil do usuário B2C
-- Colunas mapeadas na entidade User.java mas ausentes da tabela inicial:
--   phone_number, phone_verified, gender, bio, timezone, date_of_birth, city, country
-- Usa ADD COLUMN IF NOT EXISTS para ser idempotente no Neon.
-- =============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number    VARCHAR(30);
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified  BOOLEAN  NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS gender          VARCHAR(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio             TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS timezone        VARCHAR(60);
ALTER TABLE users ADD COLUMN IF NOT EXISTS date_of_birth   DATE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS city            VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS country         VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at      TIMESTAMP WITH TIME ZONE;
