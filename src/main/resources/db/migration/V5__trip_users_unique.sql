-- One membership row per user per trip
CREATE UNIQUE INDEX IF NOT EXISTS uk_trip_users_trip_user ON trip_users (trip_id, user_id);
