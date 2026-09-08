ALTER TABLE workspace_search_source_run
    ADD COLUMN query_text TEXT,
    ADD COLUMN first_zero_stage VARCHAR(30);

ALTER TABLE workspace_search_source_run
    ADD CONSTRAINT workspace_search_source_run_zero_stage_chk
        CHECK (first_zero_stage IS NULL OR first_zero_stage IN (
            'PROVIDER_RESPONSE', 'RAW_LANDING', 'NORMALIZATION', 'WORKSPACE_SIGHTING', 'SCORING'
        ));
