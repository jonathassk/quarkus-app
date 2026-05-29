-- Neon Auth: coluna de vínculo com o JWT (auth_user_id = sub/id do Neon Auth)
-- Espelha db/migration/V7__neon_auth_user_id.sql

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'cognito_sub'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'auth_user_id'
    ) THEN
        ALTER TABLE users RENAME COLUMN cognito_sub TO auth_user_id;
    END IF;
END $$;

ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_user_id VARCHAR(128);

DROP INDEX IF EXISTS uk_users_cognito_sub;
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_auth_user_id
    ON users (auth_user_id) WHERE auth_user_id IS NOT NULL;
