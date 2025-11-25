-- Initial schema for uber-snabel
-- This migration establishes the baseline schema

CREATE TABLE IF NOT EXISTS import_sessions (
    id BIGSERIAL PRIMARY KEY,
    sessionid VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    createdat TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updatedat TIMESTAMP(6) WITHOUT TIME ZONE DEFAULT NOW(),
    startedat TIMESTAMP(6) WITHOUT TIME ZONE,
    completedat TIMESTAMP(6) WITHOUT TIME ZONE,
    mergedat TIMESTAMP(6) WITHOUT TIME ZONE,

    -- Session details
    claudesessionid VARCHAR(255),
    branchname VARCHAR(255),
    targetmfe VARCHAR(255),
    zipfilename VARCHAR(255),
    unpackedpath VARCHAR(255),
    workingdirectory VARCHAR(255),
    originalinstructions VARCHAR(2000),

    -- Status flags
    validated BOOLEAN,
    buildpassed BOOLEAN,
    testspassed BOOLEAN,
    merged BOOLEAN,

    -- Metrics
    filescreated INTEGER,
    filesmodified INTEGER,
    filesdeleted INTEGER,

    -- Output
    outputlog TEXT,
    errormessage VARCHAR(5000),

    CONSTRAINT import_sessions_status_check CHECK (
        status IN ('CREATED', 'UNPACKING', 'ANALYZING', 'TRANSFORMING', 'RUNNING',
                   'PAUSED', 'VALIDATING', 'COMPLETED', 'FAILED', 'MERGED')
    )
);

-- Create index on sessionid (already unique constraint, but explicitly for clarity)
CREATE INDEX IF NOT EXISTS idx_import_sessions_sessionid ON import_sessions(sessionid);
CREATE INDEX IF NOT EXISTS idx_import_sessions_status ON import_sessions(status);
CREATE INDEX IF NOT EXISTS idx_import_sessions_createdat ON import_sessions(createdat);
