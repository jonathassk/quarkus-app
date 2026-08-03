-- Poll options on event timeline posts + one vote per user per post

ALTER TABLE event_posts
    ADD COLUMN IF NOT EXISTS poll_options JSONB;

CREATE TABLE IF NOT EXISTS event_post_poll_votes (
    post_id      UUID NOT NULL REFERENCES event_posts(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users(id),
    option_index INT  NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT chk_event_post_poll_vote_index CHECK (option_index >= 0)
);

CREATE INDEX IF NOT EXISTS idx_event_post_poll_votes_post
    ON event_post_poll_votes (post_id);
