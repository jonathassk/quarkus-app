-- Garante exclusão de viagem mesmo quando o Hibernate não carrega coleções lazy:
-- FKs do baseline sem ON DELETE CASCADE bloqueavam o DELETE em trips.

ALTER TABLE trip_segments
    DROP CONSTRAINT IF EXISTS trip_segments_trip_id_fkey;
ALTER TABLE trip_segments
    ADD CONSTRAINT trip_segments_trip_id_fkey
        FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE;

ALTER TABLE trip_users
    DROP CONSTRAINT IF EXISTS trip_users_trip_id_fkey;
ALTER TABLE trip_users
    ADD CONSTRAINT trip_users_trip_id_fkey
        FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE;

ALTER TABLE meals
    DROP CONSTRAINT IF EXISTS meals_segment_id_fkey;
ALTER TABLE meals
    ADD CONSTRAINT meals_segment_id_fkey
        FOREIGN KEY (segment_id) REFERENCES trip_segments (id) ON DELETE CASCADE;

ALTER TABLE activities
    DROP CONSTRAINT IF EXISTS activities_segment_id_fkey;
ALTER TABLE activities
    ADD CONSTRAINT activities_segment_id_fkey
        FOREIGN KEY (segment_id) REFERENCES trip_segments (id) ON DELETE CASCADE;
