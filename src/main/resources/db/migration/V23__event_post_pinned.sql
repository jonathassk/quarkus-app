-- Allow organizers to pin important timeline posts to the top.

ALTER TABLE event_posts
    ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE event_posts
    ADD COLUMN IF NOT EXISTS pinned_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_event_posts_event_pinned
    ON event_posts (event_id, pinned DESC, pinned_at DESC NULLS LAST, posted_at DESC)
    WHERE deleted_at IS NULL;
