-- Re-sync Hibernate/Panache sequence after rows were inserted via BIGSERIAL or failed uploads.

SELECT setval(
    'trip_documents_seq',
    COALESCE((SELECT MAX(id) FROM trip_documents), 1),
    true
);
