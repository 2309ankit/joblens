ALTER TABLE raw_job_posting
    DROP CONSTRAINT raw_job_posting_processing_status_chk;

ALTER TABLE raw_job_posting
    ALTER COLUMN processing_status SET DEFAULT 'NEW',
    ADD COLUMN processing_reason TEXT,
    ADD COLUMN processed_at TIMESTAMPTZ;

UPDATE raw_job_posting
SET processing_status = 'NEW'
WHERE processing_status = 'PENDING';

ALTER TABLE raw_job_posting
    ADD CONSTRAINT raw_job_posting_processing_status_chk
        CHECK (processing_status IN ('NEW', 'NORMALIZED', 'REJECTED', 'FAILED'));

CREATE TABLE normalized_job (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    raw_job_posting_id BIGINT NOT NULL,
    source VARCHAR(30) NOT NULL,
    external_job_id VARCHAR(200) NOT NULL,
    title TEXT NOT NULL,
    company TEXT,
    location TEXT,
    description_text TEXT,
    employment_type VARCHAR(30),
    salary_min NUMERIC(14, 2),
    salary_max NUMERIC(14, 2),
    salary_currency VARCHAR(3),
    remote_type VARCHAR(20),
    posted_at TIMESTAMPTZ,
    source_url TEXT,
    normalized_content_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT normalized_job_raw_fk FOREIGN KEY (raw_job_posting_id)
        REFERENCES raw_job_posting(id),
    CONSTRAINT normalized_job_raw_un UNIQUE (raw_job_posting_id),
    CONSTRAINT normalized_job_source_chk CHECK (source IN ('ADZUNA')),
    CONSTRAINT normalized_job_employment_type_chk CHECK (
        employment_type IS NULL OR employment_type IN ('PERMANENT', 'CONTRACT', 'PART_TIME', 'TEMPORARY')
    ),
    CONSTRAINT normalized_job_remote_type_chk CHECK (
        remote_type IS NULL OR remote_type IN ('REMOTE', 'HYBRID', 'ONSITE')
    ),
    CONSTRAINT normalized_job_salary_chk CHECK (
        (salary_min IS NULL OR salary_min >= 0)
        AND (salary_max IS NULL OR salary_max >= 0)
        AND (salary_min IS NULL OR salary_max IS NULL OR salary_min <= salary_max)
    ),
    CONSTRAINT normalized_job_hash_chk CHECK (normalized_content_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX normalized_job_source_external_idx
    ON normalized_job(source, external_job_id);
CREATE INDEX normalized_job_posted_at_idx
    ON normalized_job(posted_at DESC)
    WHERE posted_at IS NOT NULL;
