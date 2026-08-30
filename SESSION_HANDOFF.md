# JobLens Session Handoff

This document is the indexed handoff for JobLens through the anonymous-workspace and one-click search redesign on 2026-08-30. Read it together with [AGENTS.md](AGENTS.md), [README.md](README.md), and [BUILD_PROGRESS.md](BUILD_PROGRESS.md).

## Current redesign summary

- Flyway V11 adds anonymous browser workspaces, validated resume metadata, versioned profile drafts, preferences, search definitions, and candidate ownership.
- Flyway V12 adds workspace source projections, job sightings, and Find-jobs run history; V13 adds discovered company-board registry and workspace visibility.
- `/setup` is the first-time flow: upload resume, save preferences, then confirm.
- `/dashboard` has **Find and rank jobs**, which launches `findJobsJob` with discovery, normalization, skills, exact duplicates, fuzzy duplicates, and candidate scoring.
- Rankings, job inspection, views, applications, transitions, and follow-up reads are candidate/workspace-scoped.
- Discovery SQL and newly touched inspection SQL are external `.sql` resources using named parameters.
- Greenhouse uses its official public Job Board GET API without credentials. JobLens discovers and validates exposed official Greenhouse URLs internally; normal users do not supply board tokens. Adzuna still requires ignored environment credentials.
- `/setup` includes workspace-scoped resume skill review; active scoring skills change only after draft confirmation.
- `/applications` provides candidate-owned save, transition, follow-up refresh, and completion controls.
- Follow-up Batch runs are candidate-scoped and identify application-history revisions for useful same-day idempotency.

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
9884ff1 Add explainable fuzzy duplicate detection
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
    → application lifecycle
    → applicationFollowUpJob
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
- Audited application lifecycle with explicit transitions and immutable history.
- Restartable, idempotent follow-up generation with stale-action cancellation and completion.
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

applicationFollowUpJob
└── applicationFollowUpGenerationStep
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
V8__create_application_lifecycle.sql
V9__create_weekly_market_insights.sql
V10__create_job_view_tracking.sql
V11__create_workspace_onboarding.sql
V12__add_workspace_discovery.sql
V13__create_discovered_source_boards.sql
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
job_application
application_status_history
application_follow_up
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

Follow-up generation:

```text
POST /api/batch/follow-ups/run
businessDate required, identifying
followUpVersion=follow-up-v1 is added by the controller
failAfterApplications optional non-identifying controlled-failure parameter
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
POST/GET /api/applications
GET /api/applications/{id}
POST /api/applications/{id}/transitions
GET /api/follow-ups
POST /api/follow-ups/{id}/complete
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
Tests run: 62
Failures: 0
Errors: 0
Skipped: 0
```

Latest local startup verification:

```text
Flyway schema version: 12
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

Manual lifecycle evidence:

```text
Application 1: SAVED → APPLIED, effective 2026-08-21
JobExecution 13 / JobInstance 12: COMPLETED, read/write 1/1
APPLICATION_CHECK_IN due 2026-08-28
JobExecution 14 no-op rerun preserved one row and both timestamps
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
- Shared SQL resource loading lives in neutral `com.ankit.joblens.jdbc`, not a business module.
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
- Lifecycle transitions are explicit, forward-only, row-locked, and recorded as immutable history.
- Follow-ups are generated by Batch from status-history identity; they are not sent externally.
- A PostgreSQL startup failure was traced to an empty datasource password default; the safe local default now matches Compose: `joblens-local`.
- Dashboard and market insights were intentionally not started.

## 10. Current working-tree state

The one-click workspace redesign is committed through `8b73631`. Workspace skill review is committed at `7f2916f`, lifecycle/follow-up Thymeleaf controls are committed at `e49bfca`, and the fresh-workspace redirect is committed at `9d1a716`. Documentation reflects the verified 62-test result and live Docker render check.

Before starting new code, run:

```bash
git status
git diff
git diff --check
```

Preserve and commit the documentation changes when requested.

## 11. Known limitations

- Live Adzuna discovery still requires user credentials.
- The original resume bytes are not stored; only validated metadata and SHA-256 are retained pending an object-storage decision.
- Greenhouse enrichment activates only when a source directly exposes an official Greenhouse-hosted URL. Adzuna's current tracking URLs do not expose the final employer board, and JobLens deliberately does not follow arbitrary redirects.
- Anonymous workspaces depend on a browser cookie and have no account recovery or cross-device sync.
- Discovery is sequential; partitioning is deferred.
- The seeded default candidate remains for legacy tests/operator flows; browser workspaces have independent profiles.
- Workspace scoring reads only jobs sighted by that workspace; global duplicate analysis still reconciles the shared public-job corpus.
- Fuzzy candidate generation currently examines in-memory pairs and applies a cheap block; this is suitable for the current personal-scale dataset but should move to database blocking if volume proves it necessary.
- Fuzzy thresholds and weights are deterministic heuristics and require calibration against reviewed examples.
- Lifecycle REST reads, commands, and Thymeleaf controls enforce candidate ownership.
- Follow-up rules are deterministic code configuration and generation uses a single transactional tasklet suitable for personal scale.
- Market insights remain a shared market-level projection rather than a private candidate projection.

## 12. Next-session starting point

The anonymous manual-use workflow is complete. Greenhouse board tokens were removed from normal setup: automatic enrichment is registered, validated, and inspectable. Do not infer a next coding milestone. Consult the optional-extension list in `README.md` and wait for the user to select original-resume storage, scheduling/notifications, a verified additional source, login/recovery, or a reviewed calibration dataset.

Before implementation:

1. Read [AGENTS.md](AGENTS.md).
2. Read [BUILD_PROGRESS.md](BUILD_PROGRESS.md).
3. Read [README.md](README.md).
4. Inspect the current Git status and preserve documentation work.
5. Run or confirm the latest clean test baseline.
6. Implement only the explicitly requested milestone.

Weekly market insights are launched with `POST /api/market-insights/run?weekStart=YYYY-MM-DD` and inspected with `GET /api/market-insights?from=YYYY-MM-DD&to=YYYY-MM-DD`. Swagger UI is available at `/swagger-ui.html` and the specification at `/v3/api-docs`.
