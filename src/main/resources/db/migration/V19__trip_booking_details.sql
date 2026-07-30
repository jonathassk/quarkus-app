-- User-entered flight and lodging details (already purchased; app does not sell tickets/hotels).
ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS flight_details TEXT,
    ADD COLUMN IF NOT EXISTS hotel_details TEXT;
