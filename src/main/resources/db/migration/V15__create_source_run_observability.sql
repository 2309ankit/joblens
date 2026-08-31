ALTER TABLE source_fetch_run
    ADD COLUMN new_records INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN changed_records INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN unchanged_records INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT source_fetch_run_change_counts_chk CHECK (
        new_records >= 0 AND changed_records >= 0 AND unchanged_records >= 0
    );

CREATE TABLE workspace_search_source_run (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workspace_search_run_id BIGINT NOT NULL REFERENCES workspace_search_run(id) ON DELETE CASCADE,
    search_profile_id VARCHAR(50) NOT NULL,
    source VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    pages_attempted INTEGER NOT NULL DEFAULT 0,
    pages_fetched INTEGER NOT NULL DEFAULT 0,
    records_received INTEGER NOT NULL DEFAULT 0,
    new_records INTEGER NOT NULL DEFAULT 0,
    changed_records INTEGER NOT NULL DEFAULT 0,
    unchanged_records INTEGER NOT NULL DEFAULT 0,
    raw_records INTEGER NOT NULL DEFAULT 0,
    normalized_records INTEGER NOT NULL DEFAULT 0,
    sighted_records INTEGER NOT NULL DEFAULT 0,
    scored_records INTEGER NOT NULL DEFAULT 0,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT workspace_search_source_run_status_chk
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'EMPTY', 'FAILED')),
    CONSTRAINT workspace_search_source_run_counts_chk CHECK (
        pages_attempted >= 0 AND pages_fetched >= 0 AND records_received >= 0
        AND new_records >= 0 AND changed_records >= 0 AND unchanged_records >= 0
        AND raw_records >= 0 AND normalized_records >= 0
        AND sighted_records >= 0 AND scored_records >= 0
    ),
    CONSTRAINT workspace_search_source_run_run_profile_un
        UNIQUE (workspace_search_run_id, search_profile_id)
);

CREATE INDEX workspace_search_source_run_run_idx
    ON workspace_search_source_run(workspace_search_run_id, id);
