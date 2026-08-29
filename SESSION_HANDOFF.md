# JobLens Session Handoff

This document is the indexed handoff for the work completed through explainable fuzzy duplicate detection on 2026-08-29. Read it together with [AGENTS.md](AGENTS.md), [README.md](README.md), and [BUILD_PROGRESS.md](BUILD_PROGRESS.md).

## Index

1. [Repository and branch](#1-repository-and-branch)
2. [Product and architecture](#2-product-and-architecture)
3. [Verified completed milestones](#3-verified-completed-milestones)
4. [Spring Batch design](#4-spring-batch-design)
5. [Database and migrations](#5-database-and-migrations)
6. [REST endpoints and parameters](#6-rest-endpoints-and-parameters)
7. [Configuration and credentials](#7-configuration-and-credentials)
8. [Verification evidence](#8-verification-evidence)
9. [Important decisions and fixes](#9-important-decisions-and-fixes)
10. [Current working-tree state](#10-current-working-tree-state)
11. [Known limitations](#11-known-limitations)
12. [Next-session starting point](#12-next-session-starting-point)

## 1. Repository and branch

```text
Repository: /Users/ankitkumar/IdeaProjects/joblens
Branch: main
Java: Temurin 21
Spring Boot: 4.1.1
Spring Batch: 6
Database: PostgreSQL 17
```

Recent commits:

```text
39ce015 Add exact duplicate detection pipeline
3b49caa Add skills and candidate job scoring pipeline
7951586 Implement raw job normalization batch pipeline
99ca4d9 Implement restartable Adzuna job discovery
ef70907 Implement search profile CSV import job
d3ac70a Add JobLens build progress tracking
```

## 2. Product and architecture

JobLens is a batch-first modular monolith: one Spring Boot application, one PostgreSQL database, REST launch/read APIs, and future Thymeleaf pages.

```text
CSV profiles
    → searchProfileImportJob
    → search_profile
    → jobDiscoveryJob
    → Adzuna
    → source_fetch_run/raw_job_posting
    → jobIntelligenceJob
        → normalization
        → skill extraction
        → exact duplicate detection
        → fuzzy duplicate suggestions
        → candidate scoring
    → REST APIs
```

The REST layer launches and observes jobs. Core bulk, restartable workflows remain Spring Batch jobs.

## 3. Verified completed milestones

- PostgreSQL Compose service, Flyway, and Spring Batch metadata.
- CSV profile import with validation, rejections, PostgreSQL upsert, chunk commits, controlled failure, and checkpoint restart.
- Sequential Adzuna discovery with WebClient, pagination, bounded retries, raw JSONB preservation, payload hashing, idempotency, and saved-page restart.
- Spring Batch 6 modernization using `JobOperator` for start/restart and `JobRepository` for history.
- Raw Adzuna normalization with Jsoup HTML cleaning and deterministic normalized-content SHA-256.
- Database-driven canonical skills and aliases.
- Seeded senior Java/backend candidate profile and preferences.
- Deterministic explainable score out of 100 with reason reconciliation.
- Deterministic exact duplicate clusters, canonical membership, and pair-level evidence.
- Deterministic fuzzy duplicate suggestions with dimension scores and explicit decisions.
- Duplicate persistence and inspection SQL externalized to classpath resources and executed with `NamedParameterJdbcTemplate`.
- Job read APIs containing normalized fields, skills, scores, and reasons.

## 4. Spring Batch design

Jobs:

```text
searchProfileImportJob
└── searchProfileImportStep

jobDiscoveryJob
└── jobDiscoveryStep

jobIntelligenceJob
├── jobNormalizationStep
├── skillExtractionStep
├── exactDuplicateDetectionStep
├── fuzzyDuplicateDetectionStep
└── scoringStep
```

Important semantics:

- Automatic startup execution is disabled.
- Meaningful identifying parameters define JobInstances.
- Restarts create a new JobExecution for the same JobInstance.
- ExecutionContext contains reader/page checkpoints.
- Chunk writers keep related business updates transactionally consistent.
- Idempotent database constraints are a safety net, not a replacement for checkpoints.

## 5. Database and migrations

```text
V1__create_spring_batch_metadata.sql
V2__create_search_profile_import_tables.sql
V3__create_job_discovery_raw_landing.sql
V4__create_normalized_job.sql
V5__create_skills_candidate_scoring.sql
V6__create_exact_duplicate_detection.sql
V7__create_fuzzy_job_similarity.sql
```

Major business tables:

```text
search_profile
search_profile_rejection
source_fetch_run
raw_job_posting
normalized_job
skill
skill_alias
job_skill
candidate_profile
candidate_skill
candidate_preference
job_score
job_score_reason
duplicate_cluster
duplicate_cluster_member
duplicate_match_evidence
job_similarity
```

Raw processing states:

```text
NEW
NORMALIZED
REJECTED
FAILED
```

## 6. REST endpoints and parameters

Profile import:

```text
POST /api/batch/search-profiles/import
inputFile      required, identifying
businessDate   required, identifying
failOnRow      optional controlled-failure parameter
```

Discovery:

```text
POST /api/batch/discovery/run
businessDate   required, identifying
profileId      optional, identifying; omitted means all active profiles
```

Intelligence:

```text
POST /api/batch/intelligence/run
businessDate       required, identifying
normalizationVersion=v1 is added by the controller
duplicateDetectionVersion=fuzzy-v1 is added by the controller
failAfterItems     optional non-identifying controlled-failure parameter
failDuplicateDetection optional non-identifying controlled-failure parameter
failFuzzyDetection optional non-identifying controlled-failure parameter
```

Read endpoints:

```text
GET /api/batch/executions
GET /api/jobs
GET /api/jobs/{id}
GET /api/duplicates
GET /api/duplicates/{id}
GET /api/duplicates/similarities?decision=&minimumScore=
GET /api/duplicates/similarities/{id}
GET /actuator/health
```

Exact and fuzzy curl and SQL examples are indexed by workflow in [README.md](README.md).

## 7. Configuration and credentials

Local database defaults:

```env
JOBLENS_DB_URL=jdbc:postgresql://localhost:5432/joblens
JOBLENS_DB_USERNAME=joblens
JOBLENS_DB_PASSWORD=joblens-local
```

Adzuna:

```env
ADZUNA_APP_ID=
ADZUNA_APP_KEY=
```

The application starts without Adzuna credentials; only a live discovery launch requires them. Never commit credentials. Spring Boot does not automatically load `.env`; Compose does. Safe local application defaults now match Compose.

## 8. Verification evidence

Latest full automated result:

```text
./mvnw clean test
BUILD SUCCESS
Tests run: 46
Failures: 0
Errors: 0
Skipped: 0
```

Latest local startup verification:

```text
Flyway schema version: 7
GET /actuator/health: UP
```

Manual intelligence execution:

```text
JobInstance: 11
JobExecution: 12
Status: COMPLETED
```

Manual fuzzy evidence:

```text
MANUAL-FUZZY-1 ↔ MANUAL-FUZZY-2
overall: 84.19, decision: POSSIBLE_DUPLICATE
title: 78.60, description: 71.00, company/location/employment: 100.00
no-change rerun preserved calculated_at
```

Manual Example Bank job:

```text
Score: 54/100
Technical: 16
Domain: 0
Seniority: 10
Location/work: 8
Employment: 10
Salary: 5
Freshness: 5
Reason sum: 54
```

Seed counts:

```text
canonical skills: 30
aliases: 9
candidate profiles: 1
candidate skills: 20
candidate preferences: 12
```

## 9. Important decisions and fixes

- The Spring Boot 4.1.1 deviation is deliberate; do not downgrade without explicit instruction.
- Use Spring JDBC, not JPA, for batch-oriented persistence.
- Prefer `NamedParameterJdbcTemplate` for repository queries with several parameters or collections; retain `JdbcTemplate` for simple positional and batch-oriented operations.
- Keep complex or reused SQL in classpath `.sql` resources; the duplicate subsystem establishes this boundary without forcing repository-wide churn.
- Use `JobOperator.start(Job, JobParameters)` and `JobOperator.restart(JobExecution)`.
- Use `JobRepository` for Batch history lookup.
- Do not introduce deprecated `TaskExecutorJobLauncher` or `SimpleJobOperator`.
- Raw JSON is preserved before normalization.
- Adzuna-specific paths remain inside `AdzunaJobPostingNormalizer`.
- Skill aliases live in PostgreSQL, not scattered Java maps.
- Score weights live in candidate preferences.
- Repeated `(source, external_job_id)` sightings remain one landing identity; distinct normalized rows are clustered by exact graph connectivity.
- Exact-cluster evidence is limited to `SOURCE_EXTERNAL_ID` and `NORMALIZED_CONTENT_HASH`.
- Fuzzy `fuzzy-v1` results are review suggestions with visible scores and explanations; they are not probabilities and never merge exact clusters.
- A PostgreSQL startup failure was traced to an empty datasource password default; the safe local default now matches Compose: `joblens-local`.
- Dashboard, lifecycle, and market insights were intentionally not started.

## 10. Current working-tree state

At handoff creation, the fuzzy milestone implementation and documentation are intentionally uncommitted:

```text
M  AGENTS.md
M  README.md
M  SESSION_HANDOFF.md
M  BUILD_PROGRESS.md
M/?? duplicate Batch, REST, SQL, migration, and test files under src/
```

Application implementation through exact duplicates is committed at `39ce015`.

Before starting new code, run:

```bash
git status
git diff
git diff --check
```

Preserve and commit the documentation changes when requested.

## 11. Known limitations

- Live Adzuna discovery still requires user credentials.
- Discovery is sequential; partitioning is deferred.
- Only one candidate profile is seeded.
- Scoring currently recalculates all normalized jobs each intelligence run.
- Fuzzy candidate generation currently examines in-memory pairs and applies a cheap block; this is suitable for the current personal-scale dataset but should move to database blocking if volume proves it necessary.
- Fuzzy thresholds and weights are deterministic heuristics and require calibration against reviewed examples.
- No lifecycle/follow-up job yet.
- No market-insight job yet.
- No Thymeleaf dashboard yet.
- The application itself is not yet included in Compose.

## 12. Next-session starting point

Exact and fuzzy duplicate detection are complete. The next milestone must be explicitly selected; lifecycle, follow-up actions, market insights, dashboard work, and application Dockerization remain deferred.

Before implementation:

1. Read [AGENTS.md](AGENTS.md).
2. Read [BUILD_PROGRESS.md](BUILD_PROGRESS.md).
3. Read [README.md](README.md).
4. Inspect the current Git status and preserve documentation work.
5. Run or confirm the latest clean test baseline.
6. Implement only the explicitly requested milestone.
