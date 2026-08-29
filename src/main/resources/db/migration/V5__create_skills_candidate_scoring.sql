ALTER TABLE normalized_job
    ADD COLUMN skill_extraction_hash CHAR(64),
    ADD COLUMN scored_content_hash CHAR(64);

CREATE TABLE skill (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    canonical_name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE skill_alias (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alias_name VARCHAR(100) NOT NULL UNIQUE,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    CONSTRAINT skill_alias_name_chk CHECK (length(trim(alias_name)) > 0)
);

CREATE TABLE job_skill (
    normalized_job_id BIGINT NOT NULL REFERENCES normalized_job(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    mention_count INTEGER NOT NULL DEFAULT 1,
    evidence TEXT,
    PRIMARY KEY (normalized_job_id, skill_id)
);
CREATE INDEX job_skill_skill_idx ON job_skill(skill_id);

CREATE TABLE candidate_profile (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    summary TEXT,
    target_roles TEXT[] NOT NULL DEFAULT '{}',
    target_domains TEXT[] NOT NULL DEFAULT '{}',
    primary_location VARCHAR(150),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE candidate_skill (
    candidate_profile_id BIGINT NOT NULL REFERENCES candidate_profile(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skill(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    importance NUMERIC(5,2) NOT NULL DEFAULT 1.0,
    PRIMARY KEY (candidate_profile_id, skill_id),
    CONSTRAINT candidate_skill_status_chk CHECK (status IN ('PRODUCTION', 'LEARNING', 'DESIRED')),
    CONSTRAINT candidate_skill_importance_chk CHECK (importance > 0)
);

CREATE TABLE candidate_preference (
    candidate_profile_id BIGINT NOT NULL REFERENCES candidate_profile(id) ON DELETE CASCADE,
    preference_key VARCHAR(80) NOT NULL,
    preference_value TEXT NOT NULL,
    PRIMARY KEY (candidate_profile_id, preference_key)
);

CREATE TABLE job_score (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    normalized_job_id BIGINT NOT NULL REFERENCES normalized_job(id) ON DELETE CASCADE,
    candidate_profile_id BIGINT NOT NULL REFERENCES candidate_profile(id) ON DELETE CASCADE,
    total_score INTEGER NOT NULL,
    technical_score INTEGER NOT NULL,
    domain_score INTEGER NOT NULL,
    seniority_score INTEGER NOT NULL,
    location_score INTEGER NOT NULL,
    employment_score INTEGER NOT NULL,
    salary_score INTEGER NOT NULL,
    freshness_score INTEGER NOT NULL,
    normalized_content_hash CHAR(64) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT job_score_unique UNIQUE (normalized_job_id, candidate_profile_id),
    CONSTRAINT job_score_total_chk CHECK (total_score BETWEEN 0 AND 100),
    CONSTRAINT job_score_categories_chk CHECK (
        technical_score BETWEEN 0 AND 40 AND domain_score BETWEEN 0 AND 15
        AND seniority_score BETWEEN 0 AND 10 AND location_score BETWEEN 0 AND 10
        AND employment_score BETWEEN 0 AND 10 AND salary_score BETWEEN 0 AND 10
        AND freshness_score BETWEEN 0 AND 5
        AND total_score = technical_score + domain_score + seniority_score + location_score
            + employment_score + salary_score + freshness_score
    )
);
CREATE INDEX job_score_rank_idx ON job_score(candidate_profile_id, total_score DESC);

CREATE TABLE job_score_reason (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_score_id BIGINT NOT NULL REFERENCES job_score(id) ON DELETE CASCADE,
    category VARCHAR(30) NOT NULL,
    points INTEGER NOT NULL,
    reason_text TEXT NOT NULL
);
CREATE INDEX job_score_reason_score_idx ON job_score_reason(job_score_id);

INSERT INTO skill (canonical_name) VALUES
 ('Java'), ('Java 17'), ('Java 21'), ('Spring'), ('Spring Boot'), ('Spring Batch'),
 ('Microservices'), ('Kafka'), ('IBM MQ'), ('JMS'), ('Apache Camel'), ('PostgreSQL'),
 ('Oracle'), ('MariaDB'), ('MongoDB'), ('Docker'), ('Kubernetes'), ('AWS'), ('Jenkins'),
 ('REST'), ('SQL'), ('Git'), ('Maven'), ('Gradle'), ('JUnit'), ('Mockito'), ('OAuth2'),
 ('JWT'), ('Linux'), ('Java EE')
ON CONFLICT (canonical_name) DO NOTHING;

INSERT INTO skill_alias (alias_name, skill_id)
SELECT v.alias_name, s.id FROM (VALUES
 ('SpringBoot', 'Spring Boot'), ('spring-boot', 'Spring Boot'), ('K8s', 'Kubernetes'),
 ('Kubernetes', 'Kubernetes'), ('Amazon Web Services', 'AWS'), ('IBM WebSphere MQ', 'IBM MQ'),
 ('WebSphere MQ', 'IBM MQ'), ('J2EE', 'Java EE'), ('Postgres', 'PostgreSQL')
) v(alias_name, canonical_name) JOIN skill s ON s.canonical_name = v.canonical_name
ON CONFLICT (alias_name) DO NOTHING;

INSERT INTO candidate_profile (name, summary, target_roles, target_domains, primary_location)
VALUES ('default', 'Senior Java/backend engineer',
 ARRAY['Senior Java Developer','Senior Backend Engineer','Senior Software Engineer','Payments Engineer','Platform / Integration Engineer','Java Team Lead'],
 ARRAY['banking','payments','financial services','transaction processing','reconciliation','fraud','messaging','integration'],
 'Singapore')
ON CONFLICT (name) DO NOTHING;

INSERT INTO candidate_skill (candidate_profile_id, skill_id, status, importance)
SELECT c.id, s.id, CASE WHEN s.canonical_name = 'Java 21' THEN 'LEARNING' ELSE 'PRODUCTION' END, 1.0
FROM candidate_profile c CROSS JOIN skill s
WHERE c.name = 'default' AND s.canonical_name IN
 ('Java','Java 17','Java 21','Spring','Spring Boot','Microservices','Kafka','IBM MQ','JMS','Apache Camel','PostgreSQL','Oracle','MariaDB','MongoDB','Docker','Kubernetes','AWS','Jenkins','SQL','REST')
ON CONFLICT DO NOTHING;

INSERT INTO candidate_preference (candidate_profile_id, preference_key, preference_value)
SELECT id, v.key, v.value FROM candidate_profile c CROSS JOIN (VALUES
 ('weight.technical','40'), ('weight.domain','15'), ('weight.seniority','10'),
 ('weight.location','10'), ('weight.employment','10'), ('weight.salary','10'), ('weight.freshness','5'),
 ('employment.preference','PERMANENT'), ('work.preference','REMOTE,HYBRID,ONSITE'),
 ('salary.missing.points','3'), ('freshness.days.full','7'), ('freshness.days.half','30')
) v(key,value) WHERE c.name='default' ON CONFLICT DO NOTHING;
