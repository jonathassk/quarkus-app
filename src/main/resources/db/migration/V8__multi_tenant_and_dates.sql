-- 1. Criar sequences
CREATE SEQUENCE IF NOT EXISTS workspaces_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS workspace_members_seq START WITH 1 INCREMENT BY 1;

-- 2. Criar tabela de Workspaces
CREATE TABLE IF NOT EXISTS workspaces (
    id BIGINT PRIMARY KEY DEFAULT nextval('workspaces_seq'),
    name VARCHAR(255) NOT NULL,
    plan_type VARCHAR(50) NOT NULL DEFAULT 'FREE',
    primary_color VARCHAR(50) DEFAULT '#000000',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Garantir que a coluna id use a sequence por padrão (caso a tabela já existisse sem default)
ALTER TABLE workspaces ALTER COLUMN id SET DEFAULT nextval('workspaces_seq');

-- 3. Criar tabela de Workspace Members
CREATE TABLE IF NOT EXISTS workspace_members (
    id BIGINT PRIMARY KEY DEFAULT nextval('workspace_members_seq'),
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Garantir que a coluna id use a sequence por padrão (caso a tabela já existisse sem default)
ALTER TABLE workspace_members ALTER COLUMN id SET DEFAULT nextval('workspace_members_seq');

-- 3. Adicionar novas colunas em Trips (mantendo workspace_id temporariamente NULL para backfill)
ALTER TABLE trips ADD COLUMN IF NOT EXISTS duration_days INTEGER NOT NULL DEFAULT 1;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS target_month INTEGER;
ALTER TABLE trips ADD COLUMN IF NOT EXISTS workspace_id BIGINT REFERENCES workspaces(id);

-- Tornar datas principais opcionais (drop NOT NULL se existirem)
ALTER TABLE trips ALTER COLUMN start_date DROP NOT NULL;
ALTER TABLE trips ALTER COLUMN end_date DROP NOT NULL;

-- 4. Backfill: Criar um Workspace pessoal para cada usuário existente que possua viagens
-- e vincular as viagens a esse workspace
DO $$
DECLARE
    r RECORD;
    new_workspace_id BIGINT;
BEGIN
    FOR r IN SELECT DISTINCT created_by FROM trips WHERE workspace_id IS NULL AND created_by IS NOT NULL LOOP
        -- Criar workspace
        INSERT INTO workspaces (name, plan_type, primary_color)
        VALUES ('Workspace Pessoal de ' || r.created_by, 'FREE', '#000000')
        RETURNING id INTO new_workspace_id;

        -- Tornar o usuário OWNER deste workspace
        INSERT INTO workspace_members (workspace_id, user_id, role)
        VALUES (new_workspace_id, r.created_by, 'OWNER');

        -- Atualizar viagens antigas do usuário com este workspace_id
        UPDATE trips SET workspace_id = new_workspace_id WHERE created_by = r.created_by AND workspace_id IS NULL;
    END LOOP;

    -- Para qualquer viagem que ainda sobrar sem workspace_id, criar um default/vincular a um workspace global temporário se necessário
    IF EXISTS (SELECT 1 FROM trips WHERE workspace_id IS NULL) THEN
        INSERT INTO workspaces (name, plan_type, primary_color)
        VALUES ('Workspace Geral', 'FREE', '#000000')
        RETURNING id INTO new_workspace_id;

        UPDATE trips SET workspace_id = new_workspace_id WHERE workspace_id IS NULL;
    END IF;
END $$;

-- 5. Agora que as viagens existentes foram migradas/backfilled, podemos aplicar a constraint NOT NULL
ALTER TABLE trips ALTER COLUMN workspace_id SET NOT NULL;

-- 6. Adicionar colunas em Trip Segments
ALTER TABLE trip_segments ADD COLUMN IF NOT EXISTS start_day INTEGER NOT NULL DEFAULT 1;
ALTER TABLE trip_segments ADD COLUMN IF NOT EXISTS end_day INTEGER NOT NULL DEFAULT 1;
ALTER TABLE trip_segments ALTER COLUMN arrival_date DROP NOT NULL;
ALTER TABLE trip_segments ALTER COLUMN departure_date DROP NOT NULL;

-- 7. Adicionar colunas e ajustar tipos em Activities
ALTER TABLE activities ADD COLUMN IF NOT EXISTS day_number INTEGER NOT NULL DEFAULT 1;
ALTER TABLE activities ALTER COLUMN date DROP NOT NULL;
ALTER TABLE activities ALTER COLUMN start_time TYPE TIME WITHOUT TIME ZONE USING start_time::time;
ALTER TABLE activities ALTER COLUMN end_time TYPE TIME WITHOUT TIME ZONE USING end_time::time;

-- 8. Adicionar colunas e ajustar tipos em Meals
ALTER TABLE meals ADD COLUMN IF NOT EXISTS day_number INTEGER NOT NULL DEFAULT 1;
ALTER TABLE meals ALTER COLUMN date DROP NOT NULL;
ALTER TABLE meals ALTER COLUMN start_time TYPE TIME WITHOUT TIME ZONE USING start_time::time;
ALTER TABLE meals ALTER COLUMN end_time TYPE TIME WITHOUT TIME ZONE USING end_time::time;
