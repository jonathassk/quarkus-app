-- PanacheEntity uses trip_documents_seq; BIGSERIAL from V2 only created trip_documents_id_seq.

CREATE SEQUENCE IF NOT EXISTS trip_documents_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE trip_documents ALTER COLUMN id DROP DEFAULT;
ALTER TABLE trip_documents ALTER COLUMN id SET DEFAULT nextval('trip_documents_seq');

SELECT setval(
    'trip_documents_seq',
    COALESCE((SELECT MAX(id) FROM trip_documents), 1),
    true
);

DROP SEQUENCE IF EXISTS trip_documents_id_seq;
