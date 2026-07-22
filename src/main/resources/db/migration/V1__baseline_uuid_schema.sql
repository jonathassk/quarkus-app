-- =============================================================================
-- V1 — Baseline UUID schema (pré-lançamento)
-- =============================================================================
-- Reescrita consolidada de todo o schema após a migração de PKs Long/BIGINT
-- (com sequences) para UUID (UUIDv7 gerado na aplicação via @UuidGenerator).
--
-- Fonte de verdade: entidades JPA em src/main/java/org/example/domain/entity/**.
-- Padrão de PK: UUID PRIMARY KEY DEFAULT gen_random_uuid() (o app gera UUIDv7;
-- o default cobre inserts via SQL puro / seed). PKs que também são FK/compostas
-- usam `uuid` sem default.
--
-- Ordem de criação respeita as dependências de FK:
--   users → workspaces → agencies → trips → trip_segments → meals → activities
--   → trip_users → trip_documents → trip_checklist_items → workspace_members
--   → agency_members → b2b_trip_logs → (enums) → events → event_* → conversations
--   → conversation_participants → messages → direct_conversation_pairs
--   → user_privacy_settings → user_follows → count_trip_members()
--
-- Como é pré-lançamento, rode `flyway clean` antes de migrar para garantir um
-- banco vazio (baseline-on-migrate NÃO pode pular o V1).
-- =============================================================================

-- gen_random_uuid() já é nativo no PostgreSQL 13+ (Neon). pgcrypto é adicionado
-- por segurança/compatibilidade caso a função venha desse módulo.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =============================================================================
-- 1. users
-- =============================================================================
CREATE TABLE IF NOT EXISTS users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255),
    auth_user_id        VARCHAR(128),
    city                VARCHAR(255),
    country             VARCHAR(255),
    full_name           VARCHAR(100) NOT NULL,
    username            VARCHAR(50) UNIQUE,
    provider            VARCHAR(20),
    provider_id         VARCHAR(255),
    profile_picture_url VARCHAR(255),
    date_of_birth       DATE,
    preferred_language  VARCHAR(10)  DEFAULT 'pt-BR',
    account_status      VARCHAR(20)  DEFAULT 'active',
    email_verified      BOOLEAN      DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at       TIMESTAMPTZ,
    phone_number        VARCHAR(255),
    phone_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    timezone            VARCHAR(255),
    gender              VARCHAR(10),
    bio                 TEXT,
    visited_countries   TEXT,
    deleted_at          TIMESTAMPTZ,
    role                VARCHAR(20)  DEFAULT 'USER',
    user_type           VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    CONSTRAINT uk_provider_and_id UNIQUE (provider, provider_id)
);

-- Índice único parcial (preserva V7): auth_user_id único apenas quando presente.
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_auth_user_id
    ON users (auth_user_id) WHERE auth_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_user_type ON users (user_type);

-- =============================================================================
-- 2. workspaces
-- =============================================================================
CREATE TABLE IF NOT EXISTS workspaces (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    plan_type     VARCHAR(20)  DEFAULT 'FREE',
    logo_url      VARCHAR(512),
    primary_color VARCHAR(7)   DEFAULT '#000000',
    created_at    TIMESTAMPTZ  DEFAULT now(),
    updated_at    TIMESTAMPTZ  DEFAULT now()
);

-- =============================================================================
-- 3. agencies
-- =============================================================================
CREATE TABLE IF NOT EXISTS agencies (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    slug          VARCHAR(100) NOT NULL,
    logo_url      VARCHAR(512),
    primary_color VARCHAR(7)   NOT NULL DEFAULT '#000000',
    plan_type     VARCHAR(50)  NOT NULL DEFAULT 'B2B_FREE',
    created_at    TIMESTAMPTZ  DEFAULT now(),
    updated_at    TIMESTAMPTZ  DEFAULT now(),
    CONSTRAINT uk_agency_slug UNIQUE (slug)
);

-- =============================================================================
-- 4. trips
-- =============================================================================
CREATE TABLE IF NOT EXISTS trips (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    budget_total   NUMERIC(10, 2),
    currency       VARCHAR(3),
    start_date     DATE,
    end_date       DATE,
    duration_days  INTEGER      NOT NULL DEFAULT 1,
    target_month   INTEGER,
    cover_image_url VARCHAR(512),
    workspace_id   UUID         NOT NULL REFERENCES workspaces(id),
    created_by     UUID         NOT NULL REFERENCES users(id),
    visibility     VARCHAR(20),
    trip_status    VARCHAR(20),
    agency_id      UUID         REFERENCES agencies(id),
    created_at     TIMESTAMPTZ  DEFAULT now(),
    updated_at     TIMESTAMPTZ  DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_trips_agency_id ON trips (agency_id);

-- =============================================================================
-- 5. trip_segments
-- =============================================================================
CREATE TABLE IF NOT EXISTS trip_segments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    city_id        VARCHAR(50),
    arrival_date   DATE,
    departure_date DATE,
    start_day      INTEGER      NOT NULL DEFAULT 1,
    end_day        INTEGER      NOT NULL DEFAULT 1,
    notes          TEXT,
    daily_cost     NUMERIC(10, 2),
    trip_id        UUID         NOT NULL REFERENCES trips(id)
);

CREATE INDEX IF NOT EXISTS idx_trip_segments_trip_id ON trip_segments (trip_id);

-- =============================================================================
-- 6. meals
-- =============================================================================
CREATE TABLE IF NOT EXISTS meals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_type       VARCHAR(50),
    name            VARCHAR(255),
    description     TEXT,
    location        VARCHAR(255),
    restaurant_name VARCHAR(255),
    address         VARCHAR(255),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    cost            NUMERIC(10, 2),
    start_time      TIME WITHOUT TIME ZONE,
    end_time        TIME WITHOUT TIME ZONE,
    day_number      INTEGER      NOT NULL DEFAULT 1,
    notes           TEXT,
    date            DATE,
    segment_id      UUID         REFERENCES trip_segments(id)
);

CREATE INDEX IF NOT EXISTS idx_meals_segment_id ON meals (segment_id);

-- =============================================================================
-- 7. activities
-- =============================================================================
CREATE TABLE IF NOT EXISTS activities (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255),
    activity_type VARCHAR(50),
    address       VARCHAR(255),
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    cost          NUMERIC(10, 2),
    start_time    TIME WITHOUT TIME ZONE,
    end_time      TIME WITHOUT TIME ZONE,
    day_number    INTEGER      NOT NULL DEFAULT 1,
    notes         TEXT,
    site          VARCHAR(512),
    date          DATE,
    segment_id    UUID         REFERENCES trip_segments(id)
);

CREATE INDEX IF NOT EXISTS idx_activities_segment_id ON activities (segment_id);

-- =============================================================================
-- 8. trip_users
-- =============================================================================
CREATE TABLE IF NOT EXISTS trip_users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users(id),
    trip_id          UUID         NOT NULL REFERENCES trips(id),
    permission_level VARCHAR(20)
);

-- Uma associação por (trip, user) — preserva V5.
CREATE UNIQUE INDEX IF NOT EXISTS uk_trip_users_trip_user
    ON trip_users (trip_id, user_id);

-- =============================================================================
-- 9. trip_documents
-- =============================================================================
CREATE TABLE IF NOT EXISTS trip_documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id      UUID         NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    title        VARCHAR(255) NOT NULL,
    s3_key       VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    uploaded_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_trip_documents_trip_id ON trip_documents (trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_documents_status  ON trip_documents (status);

-- =============================================================================
-- 10. trip_checklist_items
-- =============================================================================
CREATE TABLE IF NOT EXISTS trip_checklist_items (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id    UUID         NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    title      VARCHAR(500) NOT NULL,
    notes      TEXT,
    completed  BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order INTEGER      NOT NULL DEFAULT 0,
    created_by UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_trip_checklist_trip_id ON trip_checklist_items (trip_id);

-- =============================================================================
-- 11. workspace_members
-- =============================================================================
CREATE TABLE IF NOT EXISTS workspace_members (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    role         VARCHAR(20) NOT NULL,
    CONSTRAINT uk_workspace_user UNIQUE (workspace_id, user_id)
);

-- =============================================================================
-- 12. agency_members
-- =============================================================================
CREATE TABLE IF NOT EXISTS agency_members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id   UUID        NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    agency_role VARCHAR(50) NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT uk_agency_user UNIQUE (agency_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_agency_members_agency ON agency_members (agency_id);
CREATE INDEX IF NOT EXISTS idx_agency_members_user   ON agency_members (user_id);

-- =============================================================================
-- 13. b2b_trip_logs
-- =============================================================================
CREATE TABLE IF NOT EXISTS b2b_trip_logs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id         UUID        NOT NULL REFERENCES agencies(id) ON DELETE CASCADE,
    trip_id           UUID        NOT NULL REFERENCES trips(id)    ON DELETE CASCADE,
    actor_user_id     UUID        NOT NULL REFERENCES users(id)    ON DELETE SET NULL,
    action            VARCHAR(60) NOT NULL,
    entity_type       VARCHAR(60),
    entity_id         UUID,
    previous_snapshot TEXT,
    new_snapshot      TEXT,
    description       VARCHAR(500),
    ip_address        VARCHAR(45),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_b2b_trip_logs_trip   ON b2b_trip_logs (trip_id);
CREATE INDEX IF NOT EXISTS idx_b2b_trip_logs_actor  ON b2b_trip_logs (actor_user_id);
CREATE INDEX IF NOT EXISTS idx_b2b_trip_logs_agency ON b2b_trip_logs (agency_id);
CREATE INDEX IF NOT EXISTS idx_b2b_trip_logs_action ON b2b_trip_logs (action);
CREATE INDEX IF NOT EXISTS idx_b2b_trip_logs_time   ON b2b_trip_logs (created_at DESC);

-- =============================================================================
-- 14. Enums nativos do módulo de eventos (preserva V20)
-- =============================================================================
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'event_visibility') THEN
        CREATE TYPE event_visibility AS ENUM ('PUBLIC', 'PRIVATE');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'event_status') THEN
        CREATE TYPE event_status AS ENUM ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'event_participant_role') THEN
        CREATE TYPE event_participant_role AS ENUM ('ORGANIZER', 'CO_ORGANIZER', 'GUEST');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'event_participant_status') THEN
        CREATE TYPE event_participant_status AS ENUM ('INVITED', 'ACCEPTED', 'DECLINED', 'MAYBE');
    END IF;
END $$;

-- =============================================================================
-- 15. events
-- =============================================================================
CREATE TABLE IF NOT EXISTS events (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title                VARCHAR(200) NOT NULL,
    description          TEXT,
    start_at             TIMESTAMPTZ  NOT NULL,
    end_at               TIMESTAMPTZ,
    location_name        VARCHAR(300) NOT NULL,
    location_address     TEXT,
    location_city        VARCHAR(150),
    location_country     VARCHAR(100),
    location_latitude    DOUBLE PRECISION,
    location_longitude   DOUBLE PRECISION,
    visibility           event_visibility NOT NULL DEFAULT 'PRIVATE',
    status               event_status     NOT NULL DEFAULT 'PUBLISHED',
    cover_image_url      TEXT,
    source_trip_id       UUID         REFERENCES trips(id) ON DELETE SET NULL,
    source_segment_index INTEGER,
    source_activity_id   VARCHAR(100),
    created_by           UUID         NOT NULL REFERENCES users(id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cancelled_at         TIMESTAMPTZ,
    CONSTRAINT events_end_after_start CHECK (end_at IS NULL OR end_at > start_at)
);

CREATE INDEX IF NOT EXISTS idx_events_created_by ON events (created_by);
CREATE INDEX IF NOT EXISTS idx_events_start_at   ON events (start_at);
CREATE INDEX IF NOT EXISTS idx_events_visibility_status
    ON events (visibility, status) WHERE status = 'PUBLISHED';
CREATE INDEX IF NOT EXISTS idx_events_source_trip
    ON events (source_trip_id) WHERE source_trip_id IS NOT NULL;

-- Unicidade de origem (preserva V24): apenas quando trip + activity presentes,
-- permitindo múltiplos eventos avulsos sem origem.
CREATE UNIQUE INDEX IF NOT EXISTS uq_events_source_activity
    ON events (source_trip_id, source_activity_id)
    WHERE source_trip_id IS NOT NULL AND source_activity_id IS NOT NULL;

-- =============================================================================
-- 16. event_participants
-- =============================================================================
CREATE TABLE IF NOT EXISTS event_participants (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id     UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users(id),
    role         event_participant_role   NOT NULL DEFAULT 'GUEST',
    status       event_participant_status NOT NULL DEFAULT 'INVITED',
    invited_by   UUID REFERENCES users(id),
    invited_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT uq_event_participant UNIQUE (event_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_event_participants_user
    ON event_participants (user_id);
CREATE INDEX IF NOT EXISTS idx_event_participants_event_status
    ON event_participants (event_id, status);

-- =============================================================================
-- 17. event_posts
-- =============================================================================
CREATE TABLE IF NOT EXISTS event_posts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id   UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    author_id  UUID NOT NULL REFERENCES users(id),
    text       TEXT NOT NULL,
    image_url  TEXT,
    location   VARCHAR(300),
    posted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_event_posts_event_posted
    ON event_posts (event_id, posted_at DESC);

-- =============================================================================
-- 18. event_post_likes
-- =============================================================================
CREATE TABLE IF NOT EXISTS event_post_likes (
    post_id    UUID NOT NULL REFERENCES event_posts(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);

-- =============================================================================
-- 19. event_post_comments
-- =============================================================================
CREATE TABLE IF NOT EXISTS event_post_comments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id    UUID NOT NULL REFERENCES event_posts(id) ON DELETE CASCADE,
    author_id  UUID NOT NULL REFERENCES users(id),
    text       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

-- =============================================================================
-- 20. conversations
-- =============================================================================
CREATE TABLE IF NOT EXISTS conversations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type                 VARCHAR(20) NOT NULL,
    ref_id               UUID,
    ref_uuid             UUID,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    title                VARCHAR(255),
    last_message_at      TIMESTAMPTZ,
    last_message_preview VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_conversation_type   CHECK (type IN ('TRIP', 'DIRECT', 'ACTIVITY', 'EVENT')),
    CONSTRAINT chk_conversation_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_conversations_last_message_at
    ON conversations (last_message_at DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_conversations_type_ref
    ON conversations (type, ref_id) WHERE ref_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_conversation_type_ref_id
    ON conversations (type, ref_id) WHERE ref_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_conversation_type_ref_uuid
    ON conversations (type, ref_uuid) WHERE ref_uuid IS NOT NULL;

-- =============================================================================
-- 21. conversation_participants
-- =============================================================================
CREATE TABLE IF NOT EXISTS conversation_participants (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id      UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id              UUID NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
    joined_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at              TIMESTAMPTZ,
    last_read_at         TIMESTAMPTZ,
    last_read_message_id UUID,
    unread_count         INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_conversation_participant UNIQUE (conversation_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_participants_conversation
    ON conversation_participants (conversation_id);
CREATE INDEX IF NOT EXISTS idx_participants_user_active
    ON conversation_participants (user_id) WHERE left_at IS NULL;

-- =============================================================================
-- 22. messages
-- =============================================================================
CREATE TABLE IF NOT EXISTS messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
    content         TEXT NOT NULL,
    content_type    VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at       TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_message_content_type CHECK (content_type IN ('TEXT', 'IMAGE', 'SYSTEM'))
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_created
    ON messages (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages (sender_id);

-- =============================================================================
-- 23. direct_conversation_pairs
-- =============================================================================
CREATE TABLE IF NOT EXISTS direct_conversation_pairs (
    conversation_id UUID PRIMARY KEY REFERENCES conversations(id) ON DELETE CASCADE,
    user_low_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_high_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_direct_pair_users  UNIQUE (user_low_id, user_high_id),
    CONSTRAINT chk_direct_pair_order CHECK (user_low_id < user_high_id)
);

CREATE INDEX IF NOT EXISTS idx_direct_pairs_users
    ON direct_conversation_pairs (user_low_id, user_high_id);

-- =============================================================================
-- 24. user_privacy_settings
-- =============================================================================
CREATE TABLE IF NOT EXISTS user_privacy_settings (
    user_id                 UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    public_profile          BOOLEAN NOT NULL DEFAULT FALSE,
    allow_dm_public         BOOLEAN NOT NULL DEFAULT TRUE,
    allow_dm_followers_only BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================================
-- 25. user_follows
-- =============================================================================
CREATE TABLE IF NOT EXISTS user_follows (
    follower_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, following_id),
    CONSTRAINT chk_user_follows_not_self CHECK (follower_id <> following_id)
);

CREATE INDEX IF NOT EXISTS idx_user_follows_following
    ON user_follows (following_id, follower_id);

-- =============================================================================
-- 26. count_trip_members(UUID) — preserva V19, parâmetro agora UUID
-- =============================================================================
CREATE OR REPLACE FUNCTION count_trip_members(p_trip_id UUID)
RETURNS INTEGER
LANGUAGE sql
STABLE
AS $$
    SELECT COUNT(DISTINCT member_id)::INTEGER
    FROM (
        SELECT t.created_by AS member_id
        FROM trips t
        WHERE t.id = p_trip_id
        UNION
        SELECT tu.user_id AS member_id
        FROM trip_users tu
        WHERE tu.trip_id = p_trip_id
    ) members;
$$;
