-- =============================================================================
-- V11 — Link público da viagem, convites por e-mail e comentários
-- =============================================================================

CREATE TABLE IF NOT EXISTS trip_share_links (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id     UUID           NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    code        VARCHAR(32)    NOT NULL,
    scope       VARCHAR(32)    NOT NULL DEFAULT 'VIEW_ONLY',
    expires_at  TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    created_by  UUID           REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_trip_share_links_code ON trip_share_links (code);
CREATE INDEX IF NOT EXISTS idx_trip_share_links_trip ON trip_share_links (trip_id);

CREATE TABLE IF NOT EXISTS trip_invites (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id           UUID           NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    email             VARCHAR(255)   NOT NULL,
    permission_level  VARCHAR(32)    NOT NULL,
    token             VARCHAR(64)    NOT NULL,
    status            VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
    invited_by        UUID           REFERENCES users(id) ON DELETE SET NULL,
    expires_at        TIMESTAMPTZ    NOT NULL,
    accepted_at       TIMESTAMPTZ,
    accepted_user_id  UUID           REFERENCES users(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_trip_invites_token ON trip_invites (token);
CREATE INDEX IF NOT EXISTS idx_trip_invites_trip ON trip_invites (trip_id);
CREATE INDEX IF NOT EXISTS idx_trip_invites_email ON trip_invites (email);
CREATE UNIQUE INDEX IF NOT EXISTS uk_trip_invites_pending_email
    ON trip_invites (trip_id, lower(email)) WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS trip_comments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id      UUID           NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    target_type  VARCHAR(32)    NOT NULL,
    target_id    VARCHAR(64),
    author_id    UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body         TEXT           NOT NULL,
    resolved_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_trip_comments_trip ON trip_comments (trip_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_trip_comments_target
    ON trip_comments (trip_id, target_type, target_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS trip_comment_reads (
    trip_id       UUID           NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id       UUID           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (trip_id, user_id)
);
