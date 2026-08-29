CREATE TABLE source_fetch_run (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source VARCHAR(30) NOT NULL,
    search_profile_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    pages_fetched INTEGER NOT NULL DEFAULT 0,
    records_received INTEGER NOT NULL DEFAULT 0,
    next_page INTEGER NOT NULL DEFAULT 1,
    failure_reason TEXT,
    job_instance_id BIGINT NOT NULL,
    job_execution_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT source_fetch_run_source_chk CHECK (source IN ('ADZUNA')),
    CONSTRAINT source_fetch_run_status_chk CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT source_fetch_run_counts_chk CHECK (
        pages_fetched >= 0 AND records_received >= 0 AND next_page >= 1
    ),
    CONSTRAINT source_fetch_run_profile_fk FOREIGN KEY (search_profile_id)
        REFERENCES search_profile(profile_id),
    CONSTRAINT source_fetch_run_job_instance_fk FOREIGN KEY (job_instance_id)
        REFERENCES batch_job_instance(job_instance_id),
    CONSTRAINT source_fetch_run_job_execution_fk FOREIGN KEY (job_execution_id)
        REFERENCES batch_job_execution(job_execution_id),
    CONSTRAINT source_fetch_run_instance_profile_un UNIQUE (job_instance_id, search_profile_id)
);

CREATE INDEX source_fetch_run_execution_idx ON source_fetch_run(job_execution_id);

CREATE TABLE raw_job_posting (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source VARCHAR(30) NOT NULL,
    external_job_id VARCHAR(200) NOT NULL,
    search_profile_id VARCHAR(50) NOT NULL,
    source_fetch_run_id BIGINT NOT NULL,
    source_url TEXT,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload_hash CHAR(64) NOT NULL,
    raw_payload_json JSONB NOT NULL,
    processing_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    job_execution_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT raw_job_posting_source_chk CHECK (source IN ('ADZUNA')),
    CONSTRAINT raw_job_posting_processing_status_chk CHECK (processing_status IN ('PENDING')),
    CONSTRAINT raw_job_posting_hash_chk CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT raw_job_posting_profile_fk FOREIGN KEY (search_profile_id)
        REFERENCES search_profile(profile_id),
    CONSTRAINT raw_job_posting_fetch_run_fk FOREIGN KEY (source_fetch_run_id)
        REFERENCES source_fetch_run(id),
    CONSTRAINT raw_job_posting_job_execution_fk FOREIGN KEY (job_execution_id)
        REFERENCES batch_job_execution(job_execution_id),
    CONSTRAINT raw_job_posting_source_external_un UNIQUE (source, external_job_id)
);

CREATE INDEX raw_job_posting_profile_idx ON raw_job_posting(search_profile_id);
CREATE INDEX raw_job_posting_fetch_run_idx ON raw_job_posting(source_fetch_run_id);
