ALTER TABLE workspace_search_run
    DROP CONSTRAINT workspace_search_run_status_chk;

ALTER TABLE workspace_search_run
    ADD CONSTRAINT workspace_search_run_status_chk
        CHECK (status IN (
            'STARTING', 'STARTED', 'STOPPING', 'COMPLETED', 'FAILED', 'STOPPED',
            'ABANDONED', 'UNKNOWN', 'STALE'
        ));
