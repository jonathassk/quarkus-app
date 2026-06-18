-- =============================================================================
-- V9: Agências, Membros de Agência, Tipo de Usuário e Suporte a Viagens B2B
-- =============================================================================

-- 1. Sequences para novas tabelas
CREATE SEQUENCE IF NOT EXISTS agencies_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS agency_members_seq START WITH 1 INCREMENT BY 1;

-- 2. Tabela de Agências de Viagens (tenant B2B)
CREATE TABLE IF NOT EXISTS agencies (
    id            BIGINT PRIMARY KEY DEFAULT nextval('agencies_seq'),
    name          VARCHAR(255) NOT NULL,
    slug          VARCHAR(100) NOT NULL,           -- identificador único para isolamento multitenant
    logo_url      VARCHAR(512),
    primary_color VARCHAR(7)  NOT NULL DEFAULT '#000000',
    plan_type     VARCHAR(50) NOT NULL DEFAULT 'B2B_FREE',
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agency_slug UNIQUE (slug)
);

ALTER TABLE agencies ALTER COLUMN id SET DEFAULT nextval('agencies_seq');

-- 3. Tabela de Membros da Agência
--    agency_role: AGENCY_OWNER | AGENCY_CONSULTANT
CREATE TABLE IF NOT EXISTS agency_members (
    id           BIGINT PRIMARY KEY DEFAULT nextval('agency_members_seq'),
    agency_id    BIGINT NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
    user_id      BIGINT NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    agency_role  VARCHAR(50) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agency_user UNIQUE (agency_id, user_id)
);

ALTER TABLE agency_members ALTER COLUMN id SET DEFAULT nextval('agency_members_seq');

-- 4. Adicionar coluna user_type na tabela users
--    Valores: GUEST | FREE | PREMIUM
--    Usuários existentes recebem FREE por padrão.
ALTER TABLE users ADD COLUMN IF NOT EXISTS user_type VARCHAR(20) NOT NULL DEFAULT 'FREE';

-- 5. Adicionar coluna agency_id na tabela trips
--    NULL  = viagem pessoal B2C (FREE ou PREMIUM)
--    NOT NULL = viagem criada por uma agência (isolamento multitenant B2B)
ALTER TABLE trips ADD COLUMN IF NOT EXISTS agency_id BIGINT REFERENCES agencies(id);

-- 6. Índices para otimizar queries multitenant
CREATE INDEX IF NOT EXISTS idx_trips_agency_id        ON trips(agency_id);
CREATE INDEX IF NOT EXISTS idx_agency_members_agency  ON agency_members(agency_id);
CREATE INDEX IF NOT EXISTS idx_agency_members_user    ON agency_members(user_id);
CREATE INDEX IF NOT EXISTS idx_users_user_type        ON users(user_type);
