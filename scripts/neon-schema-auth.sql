-- Neon Auth: coluna de vínculo com o JWT (auth_user_id = sub/id do Neon Auth).
-- Espelha db/migration/V1__baseline_uuid_schema.sql (seção users).
--
-- NOTA: o schema foi migrado para UUID. A coluna auth_user_id continua sendo um
-- VARCHAR(128) (o `sub` do JWT do Neon Auth é uma string), portanto seu tipo NÃO
-- mudou. A lógica histórica de renomear `cognito_sub` → `auth_user_id` tornou-se
-- obsoleta (projeto pré-lançamento, schema recriado do zero) e foi removida.

-- Garante a coluna e o índice único parcial (caso precise aplicar isoladamente).
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_user_id VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_auth_user_id
    ON users (auth_user_id) WHERE auth_user_id IS NOT NULL;
