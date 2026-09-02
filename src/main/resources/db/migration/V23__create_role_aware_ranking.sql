INSERT INTO role_catalog (canonical_name, category)
SELECT value.name, value.category
FROM (VALUES
    ('AI Engineer', 'DATA'),
    ('Machine Learning Engineer', 'DATA'),
    ('Sales Executive', 'SALES'),
    ('Account Executive', 'SALES'),
    ('Customer Success Specialist', 'CUSTOMER_SUCCESS')
) value(name, category)
WHERE NOT EXISTS (
    SELECT 1 FROM role_catalog role
    WHERE role.created_by_workspace_id IS NULL
      AND lower(role.canonical_name) = lower(value.name)
);

INSERT INTO role_alias (alias_name, role_id)
SELECT value.alias_name, role.id
FROM (VALUES
    ('ML Engineer', 'Machine Learning Engineer'),
    ('Artificial Intelligence Engineer', 'AI Engineer'),
    ('Client Success Manager', 'Customer Success Manager'),
    ('Sales Representative', 'Sales Executive')
) value(alias_name, canonical_name)
JOIN role_catalog role
  ON role.created_by_workspace_id IS NULL
 AND role.canonical_name = value.canonical_name
WHERE NOT EXISTS (
    SELECT 1 FROM role_alias alias
    WHERE alias.role_id = role.id
      AND lower(alias.alias_name) = lower(value.alias_name)
);

CREATE TABLE role_calibration_pack (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    version VARCHAR(30) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT role_calibration_pack_identity_un UNIQUE (code, version),
    CONSTRAINT role_calibration_pack_code_chk CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

CREATE UNIQUE INDEX role_calibration_pack_one_active_idx
    ON role_calibration_pack(code) WHERE active = TRUE;

CREATE TABLE role_calibration_pack_role (
    pack_id BIGINT NOT NULL REFERENCES role_calibration_pack(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES role_catalog(id) ON DELETE CASCADE,
    PRIMARY KEY (pack_id, role_id)
);

CREATE TABLE role_calibration_title_signal (
    pack_id BIGINT NOT NULL REFERENCES role_calibration_pack(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES role_catalog(id) ON DELETE CASCADE,
    signal_text VARCHAR(150) NOT NULL,
    signal_level VARCHAR(20) NOT NULL,
    PRIMARY KEY (pack_id, role_id, signal_text),
    CONSTRAINT role_calibration_title_level_chk CHECK (
        signal_level IN ('PRIMARY', 'RELATED')
    )
);

CREATE TABLE role_calibration_skill_signal (
    pack_id BIGINT NOT NULL REFERENCES role_calibration_pack(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES role_catalog(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    signal_level VARCHAR(20) NOT NULL,
    PRIMARY KEY (pack_id, role_id, skill_id),
    CONSTRAINT role_calibration_skill_level_chk CHECK (
        signal_level IN ('CORE', 'PREFERRED', 'SUPPORTING')
    )
);

INSERT INTO role_calibration_pack (code, version, display_name) VALUES
    ('FRONTEND', '1.0.0', 'Frontend'),
    ('BACKEND', '1.0.0', 'Backend Engineering'),
    ('AI_ML', '1.0.0', 'AI and Machine Learning'),
    ('SALES_CUSTOMER_SUCCESS', '1.0.0', 'Sales and Customer Success');

INSERT INTO role_calibration_pack_role (pack_id, role_id)
SELECT pack.id, role.id
FROM (VALUES
    ('FRONTEND', 'Frontend Engineer'),
    ('BACKEND', 'Backend Engineer'),
    ('AI_ML', 'Data Scientist'),
    ('AI_ML', 'AI Engineer'),
    ('AI_ML', 'Machine Learning Engineer'),
    ('SALES_CUSTOMER_SUCCESS', 'Sales Manager'),
    ('SALES_CUSTOMER_SUCCESS', 'Sales Executive'),
    ('SALES_CUSTOMER_SUCCESS', 'Account Executive'),
    ('SALES_CUSTOMER_SUCCESS', 'Customer Success Manager'),
    ('SALES_CUSTOMER_SUCCESS', 'Customer Success Specialist')
) value(pack_code, role_name)
JOIN role_calibration_pack pack ON pack.code = value.pack_code AND pack.active
JOIN role_catalog role
  ON role.canonical_name = value.role_name
 AND role.created_by_workspace_id IS NULL;

INSERT INTO role_calibration_title_signal (
    pack_id, role_id, signal_text, signal_level
)
SELECT pack.id, role.id, value.signal_text, value.signal_level
FROM (VALUES
    ('FRONTEND', 'Frontend Engineer', 'UI Engineer', 'PRIMARY'),
    ('FRONTEND', 'Frontend Engineer', 'UI Developer', 'PRIMARY'),
    ('FRONTEND', 'Frontend Engineer', 'Web UI Engineer', 'RELATED'),
    ('BACKEND', 'Backend Engineer', 'API Engineer', 'PRIMARY'),
    ('BACKEND', 'Backend Engineer', 'Server-side Engineer', 'RELATED'),
    ('AI_ML', 'Data Scientist', 'Machine Learning Scientist', 'PRIMARY'),
    ('AI_ML', 'AI Engineer', 'Generative AI Engineer', 'PRIMARY'),
    ('AI_ML', 'Machine Learning Engineer', 'Applied ML Engineer', 'PRIMARY'),
    ('AI_ML', 'Machine Learning Engineer', 'MLOps Engineer', 'RELATED'),
    ('SALES_CUSTOMER_SUCCESS', 'Sales Manager', 'Sales Lead', 'PRIMARY'),
    ('SALES_CUSTOMER_SUCCESS', 'Sales Executive', 'Sales Representative', 'PRIMARY'),
    ('SALES_CUSTOMER_SUCCESS', 'Account Executive', 'Account Sales Executive', 'PRIMARY'),
    ('SALES_CUSTOMER_SUCCESS', 'Customer Success Manager', 'Client Success Manager', 'PRIMARY'),
    ('SALES_CUSTOMER_SUCCESS', 'Customer Success Specialist', 'Customer Success Associate', 'PRIMARY')
) value(pack_code, role_name, signal_text, signal_level)
JOIN role_calibration_pack pack ON pack.code = value.pack_code AND pack.active
JOIN role_catalog role
  ON role.canonical_name = value.role_name
 AND role.created_by_workspace_id IS NULL;

INSERT INTO role_calibration_skill_signal (pack_id, role_id, skill_id, signal_level)
SELECT pack.id, role.id, skill.id, value.signal_level
FROM (VALUES
    ('FRONTEND', 'Frontend Engineer', 'HTML', 'CORE'),
    ('FRONTEND', 'Frontend Engineer', 'CSS', 'CORE'),
    ('FRONTEND', 'Frontend Engineer', 'JavaScript', 'CORE'),
    ('FRONTEND', 'Frontend Engineer', 'TypeScript', 'PREFERRED'),
    ('FRONTEND', 'Frontend Engineer', 'React', 'PREFERRED'),
    ('FRONTEND', 'Frontend Engineer', 'Angular', 'PREFERRED'),
    ('FRONTEND', 'Frontend Engineer', 'Vue.js', 'PREFERRED'),
    ('FRONTEND', 'Frontend Engineer', 'Next.js', 'SUPPORTING'),
    ('FRONTEND', 'Frontend Engineer', 'Redux', 'SUPPORTING'),
    ('FRONTEND', 'Frontend Engineer', 'Web Accessibility', 'SUPPORTING'),
    ('BACKEND', 'Backend Engineer', 'Java', 'CORE'),
    ('BACKEND', 'Backend Engineer', 'Spring Boot', 'CORE'),
    ('BACKEND', 'Backend Engineer', 'SQL', 'CORE'),
    ('BACKEND', 'Backend Engineer', 'REST', 'PREFERRED'),
    ('BACKEND', 'Backend Engineer', 'PostgreSQL', 'PREFERRED'),
    ('BACKEND', 'Backend Engineer', 'Kafka', 'SUPPORTING'),
    ('BACKEND', 'Backend Engineer', 'Docker', 'SUPPORTING'),
    ('AI_ML', 'Data Scientist', 'Python', 'CORE'),
    ('AI_ML', 'Data Scientist', 'Machine Learning', 'CORE'),
    ('AI_ML', 'Data Scientist', 'Statistics', 'CORE'),
    ('AI_ML', 'Data Scientist', 'SQL', 'PREFERRED'),
    ('AI_ML', 'Data Scientist', 'Data Analysis', 'PREFERRED'),
    ('AI_ML', 'AI Engineer', 'Python', 'CORE'),
    ('AI_ML', 'AI Engineer', 'Machine Learning', 'CORE'),
    ('AI_ML', 'AI Engineer', 'Docker', 'SUPPORTING'),
    ('AI_ML', 'Machine Learning Engineer', 'Python', 'CORE'),
    ('AI_ML', 'Machine Learning Engineer', 'Machine Learning', 'CORE'),
    ('AI_ML', 'Machine Learning Engineer', 'SQL', 'PREFERRED'),
    ('AI_ML', 'Machine Learning Engineer', 'Docker', 'SUPPORTING'),
    ('SALES_CUSTOMER_SUCCESS', 'Sales Manager', 'Sales', 'CORE'),
    ('SALES_CUSTOMER_SUCCESS', 'Sales Manager', 'CRM', 'CORE'),
    ('SALES_CUSTOMER_SUCCESS', 'Sales Manager', 'Stakeholder Management', 'PREFERRED'),
    ('SALES_CUSTOMER_SUCCESS', 'Sales Executive', 'Sales', 'CORE'),
    ('SALES_CUSTOMER_SUCCESS', 'Sales Executive', 'CRM', 'PREFERRED'),
    ('SALES_CUSTOMER_SUCCESS', 'Account Executive', 'Sales', 'CORE'),
    ('SALES_CUSTOMER_SUCCESS', 'Account Executive', 'CRM', 'CORE'),
    ('SALES_CUSTOMER_SUCCESS', 'Customer Success Manager', 'Customer Service', 'CORE'),
    ('SALES_CUSTOMER_SUCCESS', 'Customer Success Manager', 'CRM', 'CORE'),
    ('SALES_CUSTOMER_SUCCESS', 'Customer Success Manager', 'Stakeholder Management', 'PREFERRED'),
    ('SALES_CUSTOMER_SUCCESS', 'Customer Success Specialist', 'Customer Service', 'CORE'),
    ('SALES_CUSTOMER_SUCCESS', 'Customer Success Specialist', 'CRM', 'PREFERRED')
) value(pack_code, role_name, skill_name, signal_level)
JOIN role_calibration_pack pack ON pack.code = value.pack_code AND pack.active
JOIN role_catalog role
  ON role.canonical_name = value.role_name
 AND role.created_by_workspace_id IS NULL
JOIN skill
  ON skill.canonical_name = value.skill_name
 AND skill.created_by_workspace_id IS NULL;

CREATE TABLE job_role_score (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    normalized_job_id BIGINT NOT NULL REFERENCES normalized_job(id) ON DELETE CASCADE,
    candidate_profile_id BIGINT NOT NULL REFERENCES candidate_profile(id) ON DELETE CASCADE,
    target_role_id BIGINT REFERENCES role_catalog(id) ON DELETE SET NULL,
    target_role_key VARCHAR(150) NOT NULL,
    target_role_name VARCHAR(150) NOT NULL,
    role_priority INTEGER NOT NULL,
    policy_version VARCHAR(30) NOT NULL,
    calibration_pack_id BIGINT REFERENCES role_calibration_pack(id),
    total_score INTEGER NOT NULL,
    title_score INTEGER NOT NULL,
    skill_score INTEGER NOT NULL,
    sector_score INTEGER NOT NULL,
    seniority_score INTEGER NOT NULL,
    location_score INTEGER NOT NULL,
    employment_score INTEGER NOT NULL,
    salary_score INTEGER NOT NULL,
    freshness_score INTEGER NOT NULL,
    normalized_content_hash CHAR(64) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT job_role_score_identity_un UNIQUE (
        normalized_job_id, candidate_profile_id, target_role_key
    ),
    CONSTRAINT job_role_score_priority_chk CHECK (role_priority BETWEEN 1 AND 3),
    CONSTRAINT job_role_score_total_chk CHECK (total_score BETWEEN 0 AND 100),
    CONSTRAINT job_role_score_dimensions_chk CHECK (
        title_score BETWEEN 0 AND 25 AND skill_score BETWEEN 0 AND 15
        AND sector_score BETWEEN 0 AND 15 AND seniority_score BETWEEN 0 AND 10
        AND location_score BETWEEN 0 AND 10 AND employment_score BETWEEN 0 AND 10
        AND salary_score BETWEEN 0 AND 10 AND freshness_score BETWEEN 0 AND 5
        AND total_score = title_score + skill_score + sector_score + seniority_score
            + location_score + employment_score + salary_score + freshness_score
    )
);

CREATE INDEX job_role_score_rank_idx
    ON job_role_score(candidate_profile_id, target_role_key, total_score DESC, normalized_job_id);

CREATE TABLE job_role_score_reason (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_role_score_id BIGINT NOT NULL REFERENCES job_role_score(id) ON DELETE CASCADE,
    category VARCHAR(30) NOT NULL,
    points INTEGER NOT NULL,
    reason_text TEXT NOT NULL
);

CREATE INDEX job_role_score_reason_score_idx
    ON job_role_score_reason(job_role_score_id, id);

ALTER TABLE job_score
    ADD COLUMN best_target_role_id BIGINT REFERENCES role_catalog(id) ON DELETE SET NULL,
    ADD COLUMN best_target_role_name VARCHAR(150),
    ADD COLUMN ranking_policy_version VARCHAR(30) NOT NULL DEFAULT 'legacy-v1',
    ADD COLUMN calibration_pack_code VARCHAR(50),
    ADD COLUMN calibration_pack_version VARCHAR(30);
