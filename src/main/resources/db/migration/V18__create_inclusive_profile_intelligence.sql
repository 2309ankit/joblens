ALTER TABLE skill
    ADD COLUMN category VARCHAR(40) NOT NULL DEFAULT 'SOFTWARE_ENGINEERING',
    ADD COLUMN created_by_workspace_id UUID REFERENCES workspace(id) ON DELETE CASCADE;

ALTER TABLE skill DROP CONSTRAINT skill_canonical_name_key;

CREATE UNIQUE INDEX skill_global_name_un
    ON skill (lower(canonical_name)) WHERE created_by_workspace_id IS NULL;
CREATE UNIQUE INDEX skill_workspace_name_un
    ON skill (created_by_workspace_id, lower(canonical_name))
    WHERE created_by_workspace_id IS NOT NULL;
CREATE INDEX skill_category_name_idx ON skill(category, canonical_name);

UPDATE skill SET category = CASE
    WHEN canonical_name IN ('PostgreSQL', 'Oracle', 'MariaDB', 'MongoDB', 'SQL') THEN 'DATA'
    WHEN canonical_name IN ('Docker', 'Kubernetes', 'AWS', 'Jenkins', 'Linux') THEN 'CLOUD_DEVOPS'
    WHEN canonical_name IN ('Git', 'Maven', 'Gradle', 'JUnit', 'Mockito') THEN 'ENGINEERING_TOOLS'
    ELSE 'SOFTWARE_ENGINEERING'
END;

INSERT INTO skill (canonical_name, category) VALUES
 ('HTML', 'FRONTEND'), ('CSS', 'FRONTEND'), ('JavaScript', 'FRONTEND'),
 ('TypeScript', 'FRONTEND'), ('React', 'FRONTEND'), ('Angular', 'FRONTEND'),
 ('Vue.js', 'FRONTEND'), ('Next.js', 'FRONTEND'), ('Redux', 'FRONTEND'),
 ('Tailwind CSS', 'FRONTEND'), ('Web Accessibility', 'FRONTEND'), ('Figma', 'DESIGN'),
 ('Node.js', 'SOFTWARE_ENGINEERING'), ('Python', 'DATA'), ('R', 'DATA'),
 ('Microsoft Excel', 'DATA'), ('Power BI', 'DATA'), ('Tableau', 'DATA'),
 ('Data Analysis', 'DATA'), ('Machine Learning', 'DATA'), ('Statistics', 'DATA'),
 ('Project Management', 'BUSINESS'), ('Product Management', 'BUSINESS'),
 ('Agile', 'BUSINESS'), ('Scrum', 'BUSINESS'), ('Stakeholder Management', 'BUSINESS'),
 ('Business Analysis', 'BUSINESS'), ('Financial Analysis', 'FINANCE'),
 ('Accounting', 'FINANCE'), ('Budgeting', 'FINANCE'),
 ('Digital Marketing', 'MARKETING'), ('SEO', 'MARKETING'), ('CRM', 'SALES'),
 ('Sales', 'SALES'), ('Customer Service', 'CUSTOMER_SUCCESS'),
 ('Recruitment', 'HUMAN_RESOURCES'), ('Operations Management', 'OPERATIONS'),
 ('Supply Chain', 'OPERATIONS'), ('Teaching', 'EDUCATION'), ('Nursing', 'HEALTHCARE'),
 ('AutoCAD', 'ENGINEERING')
ON CONFLICT DO NOTHING;

INSERT INTO skill_alias (alias_name, skill_id)
SELECT alias_name, skill.id
FROM (VALUES
 ('JS', 'JavaScript'), ('ECMAScript', 'JavaScript'), ('TS', 'TypeScript'),
 ('React.js', 'React'), ('ReactJS', 'React'), ('AngularJS', 'Angular'),
 ('Vue', 'Vue.js'), ('VueJS', 'Vue.js'), ('NextJS', 'Next.js'),
 ('NodeJS', 'Node.js'), ('Node', 'Node.js'), ('a11y', 'Web Accessibility'),
 ('MS Excel', 'Microsoft Excel'), ('Excel', 'Microsoft Excel'),
 ('PowerBI', 'Power BI'), ('ML', 'Machine Learning'),
 ('PMP', 'Project Management'), ('Product Strategy', 'Product Management'),
 ('Search Engine Optimization', 'SEO'), ('Customer Relationship Management', 'CRM'),
 ('Talent Acquisition', 'Recruitment'), ('Operations', 'Operations Management'),
 ('Logistics', 'Supply Chain'), ('Registered Nurse', 'Nursing')
) value(alias_name, canonical_name)
JOIN skill ON skill.canonical_name = value.canonical_name
ON CONFLICT (alias_name) DO NOTHING;

CREATE TABLE role_catalog (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    canonical_name VARCHAR(150) NOT NULL,
    category VARCHAR(40) NOT NULL,
    created_by_workspace_id UUID REFERENCES workspace(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT role_catalog_name_chk CHECK (btrim(canonical_name) <> '')
);

CREATE UNIQUE INDEX role_catalog_global_name_un
    ON role_catalog (lower(canonical_name)) WHERE created_by_workspace_id IS NULL;
CREATE UNIQUE INDEX role_catalog_workspace_name_un
    ON role_catalog (created_by_workspace_id, lower(canonical_name))
    WHERE created_by_workspace_id IS NOT NULL;
CREATE INDEX role_catalog_category_name_idx ON role_catalog(category, canonical_name);

CREATE TABLE role_alias (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alias_name VARCHAR(150) NOT NULL UNIQUE,
    role_id BIGINT NOT NULL REFERENCES role_catalog(id) ON DELETE CASCADE,
    CONSTRAINT role_alias_name_chk CHECK (btrim(alias_name) <> '')
);

INSERT INTO role_catalog (canonical_name, category) VALUES
 ('Software Engineer', 'SOFTWARE_ENGINEERING'), ('Frontend Engineer', 'FRONTEND'),
 ('Backend Engineer', 'SOFTWARE_ENGINEERING'), ('Full Stack Engineer', 'SOFTWARE_ENGINEERING'),
 ('Mobile Engineer', 'SOFTWARE_ENGINEERING'), ('DevOps Engineer', 'CLOUD_DEVOPS'),
 ('Data Analyst', 'DATA'), ('Data Scientist', 'DATA'), ('Data Engineer', 'DATA'),
 ('Product Manager', 'PRODUCT'), ('Project Manager', 'PROJECT_MANAGEMENT'),
 ('Business Analyst', 'BUSINESS'), ('UX Designer', 'DESIGN'),
 ('Graphic Designer', 'DESIGN'), ('Accountant', 'FINANCE'),
 ('Financial Analyst', 'FINANCE'), ('Marketing Manager', 'MARKETING'),
 ('Sales Manager', 'SALES'), ('Human Resources Manager', 'HUMAN_RESOURCES'),
 ('Operations Manager', 'OPERATIONS'), ('Customer Success Manager', 'CUSTOMER_SUCCESS'),
 ('Registered Nurse', 'HEALTHCARE'), ('Teacher', 'EDUCATION'),
 ('Administrative Assistant', 'ADMINISTRATION'), ('Mechanical Engineer', 'ENGINEERING'),
 ('Civil Engineer', 'ENGINEERING'), ('Electrical Engineer', 'ENGINEERING')
ON CONFLICT DO NOTHING;

INSERT INTO role_alias (alias_name, role_id)
SELECT alias_name, role.id
FROM (VALUES
 ('Software Developer', 'Software Engineer'), ('Web Developer', 'Frontend Engineer'),
 ('Front End Developer', 'Frontend Engineer'), ('Frontend Developer', 'Frontend Engineer'),
 ('Back End Developer', 'Backend Engineer'), ('Backend Developer', 'Backend Engineer'),
 ('Full Stack Developer', 'Full Stack Engineer'), ('Fullstack Developer', 'Full Stack Engineer'),
 ('Site Reliability Engineer', 'DevOps Engineer'), ('SRE', 'DevOps Engineer'),
 ('Product Owner', 'Product Manager'), ('Programme Manager', 'Project Manager'),
 ('Program Manager', 'Project Manager'), ('UX/UI Designer', 'UX Designer'),
 ('UI/UX Designer', 'UX Designer'), ('HR Manager', 'Human Resources Manager'),
 ('Talent Acquisition Manager', 'Human Resources Manager'),
 ('Customer Service Manager', 'Customer Success Manager'),
 ('RN', 'Registered Nurse'), ('Educator', 'Teacher'),
 ('Executive Assistant', 'Administrative Assistant')
) value(alias_name, canonical_name)
JOIN role_catalog role ON role.canonical_name = value.canonical_name
ON CONFLICT (alias_name) DO NOTHING;

CREATE TABLE workspace_profile_skill_suggestion (
    profile_version_id BIGINT NOT NULL
        REFERENCES workspace_profile_version(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    matched_term VARCHAR(150) NOT NULL,
    evidence VARCHAR(300) NOT NULL,
    confidence NUMERIC(4,3) NOT NULL,
    PRIMARY KEY (profile_version_id, skill_id),
    CONSTRAINT profile_skill_suggestion_confidence_chk CHECK (confidence BETWEEN 0 AND 1)
);

CREATE TABLE workspace_profile_role_suggestion (
    profile_version_id BIGINT NOT NULL
        REFERENCES workspace_profile_version(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES role_catalog(id) ON DELETE CASCADE,
    evidence_source VARCHAR(30) NOT NULL,
    evidence VARCHAR(300) NOT NULL,
    confidence NUMERIC(4,3) NOT NULL,
    priority INTEGER NOT NULL,
    PRIMARY KEY (profile_version_id, role_id),
    CONSTRAINT profile_role_suggestion_source_chk CHECK (
        evidence_source IN ('RESUME_HEADLINE', 'RECENT_EXPERIENCE', 'RESUME_BODY')
    ),
    CONSTRAINT profile_role_suggestion_confidence_chk CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT profile_role_suggestion_priority_chk CHECK (priority > 0)
);

CREATE INDEX profile_role_suggestion_order_idx
    ON workspace_profile_role_suggestion(profile_version_id, priority);
