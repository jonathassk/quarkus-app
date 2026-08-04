-- =============================================================================
-- V25 — Agency clients: dados de identidade / documento (OCR passaporte/RG/CNH)
-- =============================================================================

ALTER TABLE agency_clients
    ADD COLUMN birth_place VARCHAR(255),
    ADD COLUMN nationality VARCHAR(128),
    ADD COLUMN document_number VARCHAR(64),
    ADD COLUMN document_type VARCHAR(32),
    ADD COLUMN document_issued_at DATE,
    ADD COLUMN document_expires_at DATE,
    ADD COLUMN birth_date DATE,
    ADD COLUMN gender VARCHAR(32);
