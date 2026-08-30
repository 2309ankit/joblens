ALTER TABLE workspace_search_definition
    ADD COLUMN greenhouse_boards TEXT[] NOT NULL DEFAULT '{}';

ALTER TABLE search_profile
    ADD COLUMN workspace_id UUID REFERENCES workspace(id) ON DELETE CASCADE,
    ADD COLUMN search_definition_id BIGINT REFERENCES workspace_search_definition(id) ON DELETE CASCADE,
    ADD COLUMN max_pages INTEGER;

ALTER TABLE search_profile
    ADD CONSTRAINT search_profile_max_pages_chk
        CHECK (max_pages IS NULL OR max_pages BETWEEN 1 AND 20);

CREATE INDEX search_profile_workspace_idx
    ON search_profile(workspace_id, active, profile_id)
    WHERE workspace_id IS NOT NULL;

ALTER TABLE search_profile DROP CONSTRAINT search_profile_source_chk;
ALTER TABLE search_profile
    ADD CONSTRAINT search_profile_source_chk CHECK (source IN ('ADZUNA', 'GREENHOUSE'));

ALTER TABLE source_fetch_run DROP CONSTRAINT source_fetch_run_source_chk;
ALTER TABLE source_fetch_run
    ADD CONSTRAINT source_fetch_run_source_chk CHECK (source IN ('ADZUNA', 'GREENHOUSE'));

ALTER TABLE raw_job_posting DROP CONSTRAINT raw_job_posting_source_chk;
ALTER TABLE raw_job_posting
    ADD CONSTRAINT raw_job_posting_source_chk CHECK (source IN ('ADZUNA', 'GREENHOUSE'));

ALTER TABLE normalized_job DROP CONSTRAINT normalized_job_source_chk;
ALTER TABLE normalized_job
    ADD CONSTRAINT normalized_job_source_chk CHECK (source IN ('ADZUNA', 'GREENHOUSE'));

CREATE TABLE workspace_job_sighting (
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    raw_job_posting_id BIGINT NOT NULL REFERENCES raw_job_posting(id) ON DELETE CASCADE,
    search_definition_id BIGINT NOT NULL REFERENCES workspace_search_definition(id) ON DELETE CASCADE,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workspace_id, raw_job_posting_id)
);

CREATE INDEX workspace_job_sighting_raw_idx
    ON workspace_job_sighting(raw_job_posting_id);

CREATE TABLE workspace_search_run (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    candidate_profile_id BIGINT NOT NULL REFERENCES candidate_profile(id) ON DELETE CASCADE,
    business_date DATE NOT NULL,
    job_instance_id BIGINT NOT NULL REFERENCES batch_job_instance(job_instance_id),
    job_execution_id BIGINT NOT NULL REFERENCES batch_job_execution(job_execution_id),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT workspace_search_run_status_chk
        CHECK (status IN ('STARTING', 'STARTED', 'COMPLETED', 'FAILED', 'STOPPED', 'ABANDONED', 'UNKNOWN')),
    CONSTRAINT workspace_search_run_execution_un UNIQUE (job_execution_id)
);

CREATE INDEX workspace_search_run_workspace_idx
    ON workspace_search_run(workspace_id, id DESC);
