-- Breakdown do custo base B2B (voo, hospedagem, seguro, passeios, extras).
ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS base_cost_items JSONB;
