-- Pending items for a trip (documents to add, places to visit, etc.)

CREATE SEQUENCE IF NOT EXISTS trip_checklist_items_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS trip_checklist_items (
    id BIGINT PRIMARY KEY DEFAULT nextval('trip_checklist_items_seq'),
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    notes TEXT,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trip_checklist_trip_id ON trip_checklist_items(trip_id);

SELECT setval(
    'trip_checklist_items_seq',
    COALESCE((SELECT MAX(id) FROM trip_checklist_items), 1),
    true
);
