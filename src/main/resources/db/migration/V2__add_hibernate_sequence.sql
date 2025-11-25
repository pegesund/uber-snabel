-- Add Hibernate sequence for PanacheEntity
-- Hibernate/Panache expects a sequence named import_sessions_SEQ

-- Get the current max id to start the sequence from a safe value
DO $$
DECLARE
    max_id BIGINT;
    start_val BIGINT;
BEGIN
    SELECT COALESCE(MAX(id), 0) INTO max_id FROM import_sessions;
    -- Start from next 50-block (Hibernate uses allocationSize=50 by default)
    start_val := ((max_id / 50) + 1) * 50;

    -- Create the sequence with proper starting value
    EXECUTE format('CREATE SEQUENCE IF NOT EXISTS import_sessions_SEQ START WITH %s INCREMENT BY 50', start_val);
END $$;
