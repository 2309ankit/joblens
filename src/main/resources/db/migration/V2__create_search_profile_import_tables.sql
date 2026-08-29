CREATE TABLE search_profile (
    profile_id VARCHAR(50) PRIMARY KEY,
    source VARCHAR(30) NOT NULL,
    source_key VARCHAR(50),
    keywords VARCHAR(500) NOT NULL,
    location VARCHAR(200),
    include_skills TEXT NOT NULL DEFAULT '',
    exclude_skills TEXT NOT NULL DEFAULT '',
    employment_type VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT search_profile_source_chk CHECK (source IN ('ADZUNA')),
    CONSTRAINT search_profile_employment_type_chk
        CHECK (employment_type IN ('ANY', 'PERMANENT', 'CONTRACT')),
    CONSTRAINT search_profile_id_not_blank_chk CHECK (btrim(profile_id) <> ''),
    CONSTRAINT search_profile_keywords_not_blank_chk CHECK (btrim(keywords) <> '')
);

CREATE TABLE search_profile_rejection (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    input_file TEXT NOT NULL,
    row_number BIGINT,
    raw_record JSONB NOT NULL,
    rejection_reason TEXT NOT NULL,
    job_execution_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT search_profile_rejection_job_execution_fk
        FOREIGN KEY (job_execution_id) REFERENCES batch_job_execution(job_execution_id)
);

CREATE INDEX search_profile_rejection_job_execution_idx
    ON search_profile_rejection(job_execution_id);
