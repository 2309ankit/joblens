ALTER TABLE search_profile DROP CONSTRAINT search_profile_source_chk;
ALTER TABLE search_profile
    ADD CONSTRAINT search_profile_source_chk
        CHECK (source IN ('ADZUNA', 'GREENHOUSE', 'JOOBLE', 'LEVER'));

ALTER TABLE source_fetch_run DROP CONSTRAINT source_fetch_run_source_chk;
ALTER TABLE source_fetch_run
    ADD CONSTRAINT source_fetch_run_source_chk
        CHECK (source IN ('ADZUNA', 'GREENHOUSE', 'JOOBLE', 'LEVER'));

ALTER TABLE raw_job_posting DROP CONSTRAINT raw_job_posting_source_chk;
ALTER TABLE raw_job_posting
    ADD CONSTRAINT raw_job_posting_source_chk
        CHECK (source IN ('ADZUNA', 'GREENHOUSE', 'JOOBLE', 'LEVER'));

ALTER TABLE normalized_job DROP CONSTRAINT normalized_job_source_chk;
ALTER TABLE normalized_job
    ADD CONSTRAINT normalized_job_source_chk
        CHECK (source IN ('ADZUNA', 'GREENHOUSE', 'JOOBLE', 'LEVER'));

ALTER TABLE discovered_source_board DROP CONSTRAINT discovered_source_board_source_chk;
ALTER TABLE discovered_source_board
    ADD CONSTRAINT discovered_source_board_source_chk
        CHECK (source IN ('GREENHOUSE', 'LEVER'));
