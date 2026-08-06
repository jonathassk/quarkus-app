-- Agenda operacional: evolução de tarefas + referência de próxima ação na oportunidade.

ALTER TABLE agency_opportunity_tasks
    ADD COLUMN IF NOT EXISTS task_type VARCHAR(32) NOT NULL DEFAULT 'COMMERCIAL',
    ADD COLUMN IF NOT EXISTS action_kind VARCHAR(64),
    ADD COLUMN IF NOT EXISTS note TEXT,
    ADD COLUMN IF NOT EXISTS waiting_on VARCHAR(32),
    ADD COLUMN IF NOT EXISTS is_next_action BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS origin VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS completed_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS completion_outcome VARCHAR(64),
    ADD COLUMN IF NOT EXISTS priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL';

-- Status passa a aceitar WAITING / CANCELLED (valores em VARCHAR já existentes).

ALTER TABLE agency_opportunities
    ADD COLUMN IF NOT EXISTS next_action_task_id UUID REFERENCES agency_opportunity_tasks (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_opp_tasks_agenda
    ON agency_opportunity_tasks (agency_id, assignee_user_id, status, due_at);

CREATE INDEX IF NOT EXISTS idx_opp_tasks_next_action
    ON agency_opportunity_tasks (opportunity_id, is_next_action)
    WHERE is_next_action = TRUE;

-- Backfill: oportunidades com next_action_at e sem tarefa vinculada → criar task OPEN.
DO $$
DECLARE
    r RECORD;
    new_id UUID;
BEGIN
    FOR r IN
        SELECT o.id AS opp_id,
               o.agency_id,
               o.next_action_type,
               o.next_action_at,
               o.next_action_note,
               o.next_action_assignee_id
        FROM agency_opportunities o
        WHERE o.next_action_at IS NOT NULL
          AND o.next_action_task_id IS NULL
    LOOP
        new_id := gen_random_uuid();
        INSERT INTO agency_opportunity_tasks (
            id, opportunity_id, agency_id, title, status, due_at, assignee_user_id,
            task_type, action_kind, note, is_next_action, origin, priority,
            created_at, updated_at
        ) VALUES (
            new_id,
            r.opp_id,
            r.agency_id,
            COALESCE(NULLIF(TRIM(r.next_action_note), ''), 'Próxima ação'),
            'OPEN',
            r.next_action_at,
            r.next_action_assignee_id,
            'COMMERCIAL',
            r.next_action_type,
            r.next_action_note,
            TRUE,
            'MANUAL',
            'NORMAL',
            NOW(),
            NOW()
        );
        UPDATE agency_opportunities
        SET next_action_task_id = new_id
        WHERE id = r.opp_id;
    END LOOP;
END $$;
