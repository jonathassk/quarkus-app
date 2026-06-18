-- =============================================================================
-- V10: Tabela de auditoria operacional B2B (b2b_trip_logs)
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS b2b_trip_logs_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS b2b_trip_logs (
    id                BIGINT       PRIMARY KEY DEFAULT nextval('b2b_trip_logs_seq'),
    agency_id         BIGINT       NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
    trip_id           BIGINT       NOT NULL REFERENCES trips(id)    ON DELETE CASCADE,
    actor_user_id     BIGINT       NOT NULL REFERENCES users(id)    ON DELETE SET NULL,

    -- Tipo de operação: mapeado para o enum B2bTripLogAction
    action            VARCHAR(60)  NOT NULL,

    -- Entidade-alvo da operação
    entity_type       VARCHAR(60),           -- ex.: SEGMENT, ACTIVITY, MEAL, TRIP, MEMBER
    entity_id         BIGINT,                -- ID do registro afetado

    -- Snapshots de estado (resumo JSON dos campos alterados)
    previous_snapshot TEXT,
    new_snapshot      TEXT,

    -- Descrição legível para exibição no painel de auditoria
    description       VARCHAR(500),

    -- Rastreabilidade extra
    ip_address        VARCHAR(45),           -- IPv4 ou IPv6

    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE b2b_trip_logs ALTER COLUMN id SET DEFAULT nextval('b2b_trip_logs_seq');

-- Índices para queries do dashboard de auditoria
CREATE INDEX IF NOT EXISTS idx_b2b_trip_logs_trip   ON b2b_trip_logs(trip_id);
CREATE INDEX IF NOT EXISTS idx_b2b_trip_logs_actor  ON b2b_trip_logs(actor_user_id);
CREATE INDEX IF NOT EXISTS idx_b2b_trip_logs_agency ON b2b_trip_logs(agency_id);
CREATE INDEX IF NOT EXISTS idx_b2b_trip_logs_action ON b2b_trip_logs(action);
CREATE INDEX IF NOT EXISTS idx_b2b_trip_logs_time   ON b2b_trip_logs(created_at DESC);
