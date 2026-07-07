-- =============================================================================
-- V12: Adiciona coluna visited_countries na tabela users
-- =============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS visited_countries TEXT;
