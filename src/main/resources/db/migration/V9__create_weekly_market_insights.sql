CREATE TABLE weekly_market_insight (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    week_start DATE NOT NULL,
    source VARCHAR(30) NOT NULL,
    job_count INTEGER NOT NULL,
    company_count INTEGER NOT NULL,
    remote_job_count INTEGER NOT NULL,
    average_salary NUMERIC(14,2),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT weekly_market_insight_un UNIQUE (week_start, source)
);
