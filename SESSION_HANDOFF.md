# JobLens Session Handoff

Use this file to resume work quickly. Historical implementation evidence remains in
[BUILD_PROGRESS.md](BUILD_PROGRESS.md), the operator guide is [README.md](README.md), and selectable
future work is indexed in [NEXT_MILESTONES.md](NEXT_MILESTONES.md).

## 1. Resume checkpoint

```text
Repository: /Users/ankitkumar/IdeaProjects/joblens
Branch: main
Implementation baseline: normalized multi-market preferences plus resume-validation/setup UX fixes
Java: 21
Spring Boot: 4.1.1 (deliberate recorded deviation from the original 3.x request)
Spring Batch: 6
Database: PostgreSQL 17
Latest Flyway migration: V17
Latest full test: 88 tests, 0 failures, 0 errors, 0 skipped
Latest Docker check: health UP, Flyway 17, both setup states and resume OpenAPI verified
```

Before making changes:

```bash
cd /Users/ankitkumar/IdeaProjects/joblens
git status --short
docker compose ps
```

Read [AGENTS.md](AGENTS.md), then select exactly one milestone from
[NEXT_MILESTONES.md](NEXT_MILESTONES.md). Do not infer or combine milestones.

## 2. Product flow that works now

```text
/setup
  → upload PDF/DOC/DOCX and reject job/interview documents without resume structure
  → review skills, current city, and structured desired-market rows in one form
  → save and activate the reviewed profile in one transaction

/dashboard
  → Find and rank jobs
  → findJobsJob
      1. public-source discovery
      2. raw JSON landing
      3. normalization
      4. skill extraction
      5. exact and fuzzy duplicate analysis
      6. candidate scoring with reasons
  → open original listing (records VIEWED)
  → save an application

/applications
  → controlled lifecycle transitions
  → refresh deterministic follow-ups
  → complete reminders
```

Each anonymous browser workspace owns its candidate profile, preferences, sightings, scores, views,
applications, and follow-ups. The browser cookie is the current identity boundary.

## 3. Current architecture

JobLens remains a batch-first modular monolith: one Spring Boot application, one PostgreSQL database,
one deployable image.

```text
Thymeleaf / REST
       ↓
application services and ownership checks
       ↓
Spring Batch jobs and steps
       ↓
source adapters → raw landing → deterministic intelligence → lifecycle projections
       ↓
PostgreSQL owned by Flyway
```

Important jobs:

| Job | Purpose |
| --- | --- |
| `findJobsJob` | Normal one-click workspace flow from discovery through scoring |
| `searchProfileImportJob` | Original CSV/Spring Batch learning workflow |
| `jobDiscoveryJob` | Operator launch of discovery only |
| `jobIntelligenceJob` | Operator launch of normalization through scoring |
| `applicationFollowUpJob` | Candidate-scoped deterministic reminders |
| `weeklyMarketInsightJob` | Shared weekly market aggregates |

Automatic Batch startup remains disabled. Jobs use meaningful identifying parameters, persisted
ExecutionContext checkpoints, observable failures, restarts, and idempotent writes.

## 4. Job sources

| Source | Current behavior | Credential |
| --- | --- | --- |
| Adzuna | Default broad public discovery source | `ADZUNA_APP_ID`, `ADZUNA_APP_KEY` |
| Jooble | Optional broad source; live Singapore flow verified | Regional `JOOBLE_API_KEY` |
| Greenhouse | Automatically detected only from direct official board URLs and validated internally | None |
| Lever | Automatically detected only from direct global official hosted URLs and validated internally | None |
| LinkedIn | Three smart outbound searches using role/sector/technology Boolean queries | None |
| JobStreet Singapore | Three smart outbound searches using concise natural queries | None |
| SEEK Australia/New Zealand | Three smart outbound searches per region using concise natural queries | None |

LinkedIn, JobStreet, Indeed, and Google results are not scraped. Provider JSON from integrated APIs is
stored before provider-specific normalization. Live Jooble acceptance completed on 2026-08-31 with
60 raw, normalized, sighted, and scored Singapore records in Find Jobs execution 22.

To enable Jooble:

```env
JOOBLE_API_KEY=your-singapore-regional-key
```

Rebuild/restart the app, then save and confirm preferences again so the workspace receives a Jooble
search profile.

## 5. Data and processing decisions

- Flyway V1-V17 owns application and Spring Batch metadata schemas.
- Search countries/locations are normalized as independent `workspace_search_target` rows. Confirming
  preferences creates one provider profile and checkpoint per supported source/market combination.
- Spring JDBC is used; JPA and Lombok are intentionally absent.
- Complex or reused SQL lives under `src/main/resources/sql/` and is loaded with named parameters.
- Original resume bytes are not stored. Only validated metadata, extracted text-derived profile data,
  and SHA-256 identity are retained.
- Exact duplicate evidence is source/external ID or normalized-content hash.
- Fuzzy matches are explainable review suggestions, not probabilities or automatic merges.
- Scoring is deterministic, preference-driven, and accompanied by category reasons.
- Unknown end clients are not guessed. Any future estimate must expose evidence and uncertainty.

## 6. Main inspection points

```text
Application: http://localhost:8080
Setup:       http://localhost:8080/setup
Dashboard:   http://localhost:8080/dashboard
Swagger:     http://localhost:8080/swagger-ui.html
OpenAPI:     http://localhost:8080/v3/api-docs
Health:      http://localhost:8080/actuator/health
```

Useful APIs:

```text
POST /find-jobs
GET  /api/batch/find-jobs/runs
GET  /api/batch/find-jobs/runs/{jobExecutionId}
POST /api/batch/find-jobs/runs/{jobExecutionId}/restart
GET  /api/jobs
GET  /api/jobs/{id}
GET  /api/duplicates
GET  /api/duplicates/similarities
GET  /api/source-boards
GET  /api/job-views
GET  /api/applications
GET  /api/follow-ups
GET  /api/batch/executions
```

## 7. Code map

```text
src/main/java/com/ankit/joblens/
  batchapi/       launch, history, job inspection, OpenAPI descriptions
  workspace/      anonymous workspace identity and candidate ownership
  onboarding/     resume validation, profile drafts, skills, preferences
  discovery/      source adapters, raw landing, source boards, Find-jobs orchestration
  intelligence/   normalization, skills, duplicate analysis, scoring
  lifecycle/      applications, transitions, follow-ups
  dashboard/      Thymeleaf controllers and view tracking

src/main/resources/
  db/migration/   Flyway V1-V17
  sql/            externalized SQL grouped by feature
  templates/      setup, dashboard, applications
```

## 8. Verification commands

```bash
./mvnw -q spotless:apply
./mvnw clean test
git diff --check

./mvnw -q -DskipTests package
docker compose build app
docker compose up -d
curl http://localhost:8080/actuator/health
```

Testcontainers requires Docker Desktop. Never commit `.env`, credentials, tokens, or resume data.

## 9. Known limitations

- Anonymous cookie workspaces have no account recovery or cross-device synchronization.
- Jooble needs a regional key and its provider quota must be monitored.
- Jooble runs only for the market matching `JOOBLE_COUNTRY_CODE`; Adzuna provides the other configured
  broad-market searches.
- Greenhouse enrichment activates only when a legitimate source exposes a direct official board URL.
- Lever enrichment activates only for a direct global `jobs.lever.co` URL; tracking redirects and the
  separate EU host are deliberately unsupported.
- Discovery is sequential and intentionally unpartitioned at current personal scale.
- Fuzzy thresholds and scoring weights need reviewed real-world calibration.
- Original resume storage, schedules, and external notifications are not implemented.
- Market insights are shared market-level projections rather than private workspace projections.
- The seeded skill catalogue is strongly Java/backend-oriented. A structurally valid non-IT resume
  can still be rejected when it contains no catalogued skill, frontend skills are incomplete, and
  target roles are not extracted from resume experience.
- Setup exposes ISO country-code input instead of a country-name selector, and target roles are plain
  comma-separated text rather than detected, searchable, creatable selections.
- Java repositories explicitly reference SQL resource paths. SQL is correctly externalized, but
  those string paths are runtime-checked and should gain a typed, startup-validated registry.

## 10. Handoff rule

M0 Jooble live acceptance, M0.5 Portal Search Hub, M0.6 Smart Portal Query Planner, M1 source
health/run observability, M2 Lever, and M2.5 normalized multi-market preferences are complete. M3
ranking calibration remains indexed but is no longer next. The user selected this exact order:

```text
M2.6 Inclusive Profile Intelligence
  → STOP, demonstrate it, and ask the user for approval
M2.7 Explainable ATS Readiness Advisor
  → STOP, demonstrate it, and ask the user for approval
M2.8 Typed SQL Resource Registry
  → STOP and review what comes next with the user
```

Do not combine these modules and do not silently advance from one to another. At each boundary,
finish tests, evidence, documentation, and a conventional commit, then explicitly ask the user before
starting the next module. React migration remains a later, separate presentation-layer decision.

The next session should begin with M2.6 only. Before coding, inspect the existing V5 skill seed,
`OnboardingService`, profile/version tables, setup form, skill extraction/scoring, and source country
capabilities. Agree on M2.6 acceptance details with the user if any material choice remains.
