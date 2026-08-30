# JobLens

JobLens is a batch-first modular monolith for personal job-market intelligence. Each anonymous browser workspace can upload and validate a resume, control job preferences in the UI, discover public Adzuna postings and optionally Jooble postings, safely enrich from automatically detected Greenhouse boards, rank only its discovered jobs, and track applications.

New session: start with [SESSION_HANDOFF.md](SESSION_HANDOFF.md). To choose the next piece of work,
use [NEXT_MILESTONES.md](NEXT_MILESTONES.md). Detailed historical evidence remains in
[BUILD_PROGRESS.md](BUILD_PROGRESS.md).

## Architecture

One Spring Boot application, one PostgreSQL database, one deployable process:

```text
Browser cookie → workspace → validated resume draft → confirmed candidate/preferences
                                                   ↓
                    findJobsJob
                    ├─ jobDiscoveryStep → JobSourceClient registry
                    │                    ├─ Adzuna API
                    │                    ├─ Jooble Search API (when configured)
                    │                    └─ exposed Greenhouse URL → internal board registry → public Job Board API
                    │                    → raw_job_posting/workspace_job_sighting
                    ├─ jobNormalizationStep → normalized_job
                    ├─ skillExtractionStep   → job_skill
                    ├─ exactDuplicateDetectionStep → duplicate_cluster/membership/evidence
                    ├─ fuzzyDuplicateDetectionStep → job_similarity
                    └─ scoringStep            → job_score/job_score_reason

CSV → searchProfileImportJob → search_profile (legacy/operator batch input remains supported)

                    applicationFollowUpJob
                    └─ applicationFollowUpGenerationStep → application_follow_up
```

Packages:

```text
batchapi       REST launch, history, and job-read endpoints
searchprofile  CSV reader, validation, rejection, and upsert
workspace      anonymous workspace cookie and candidate ownership
onboarding     resume validation, versioned profile drafts, preferences, source definitions
discovery      source adapters, workspace sightings, raw landing, one-click orchestration
intelligence   normalization, skills, duplicate detection, candidate profile, and scoring
lifecycle      application transitions, history, and follow-up generation
```

Flyway migrations are incremental. V1-V10 build the original Batch, intelligence, lifecycle, insights, and view-tracking slices; V11 adds anonymous workspace onboarding; V12 adds workspace discovery, source projections, job sightings, and Find-jobs run history; V13 adds safe automatic company-board discovery; V14 adds the optional Jooble source to the source constraints.

A Batch Job is a workflow definition; a JobInstance is one logical run identified by parameters; a JobExecution is one attempt; each StepExecution records counts; ExecutionContext stores restart checkpoints.

## Start locally

```bash
cd ~/IdeaProjects/joblens
test -f .env || cp .env.example .env
docker compose up -d
docker compose ps
./mvnw spring-boot:run
```

To run the complete Dockerized stack instead:

```bash
docker compose build app
docker compose up -d
curl http://localhost:8080/actuator/health
```

The application is at `http://localhost:8080`; PostgreSQL is at port `5432`. Local defaults match Compose:

```env
JOBLENS_DB_URL=jdbc:postgresql://localhost:5432/joblens
JOBLENS_DB_USERNAME=joblens
JOBLENS_DB_PASSWORD=joblens-local
# Optional: a Singapore regional key from https://sg.jooble.org/api/about
JOOBLE_API_KEY=
```

Environment variables override these values. Never commit real credentials; `.env` is ignored.

## First-time use

1. Open `http://localhost:8080/setup`. JobLens creates an anonymous workspace cookie in this browser.
2. Upload a PDF, DOC, or DOCX resume, maximum 5 MB. Apache Tika extracts text; the draft is accepted only when readable text and known skills are found. JobLens currently stores resume metadata and hash, not the original file bytes.
3. Review the detected skill checkboxes. Add skills the reader missed or remove incorrect matches, then save the reviewed draft. An active profile is never changed until its new draft is confirmed.
4. Enter target roles, domains, location, keywords, and page limit. JobLens searches Adzuna and, when `JOOBLE_API_KEY` is configured, Jooble. If one exposes an official Greenhouse-hosted job URL, JobLens extracts the board identifier, validates it internally using the documented public [Greenhouse Job Board API](https://docs.greenhouse.io/job-board.html), and searches it—no provider token or Greenhouse credential is requested from you.
5. Confirm the draft. Confirmation versions the profile and activates candidate skills, preferences, and runnable source definitions. If you add `JOOBLE_API_KEY` later, save and confirm preferences once more to activate its source profile.
6. Open `http://localhost:8080/dashboard` and click **Find and rank jobs**. This runs discovery through scoring as one restartable Spring Batch Job.
7. Open a result with its source link. This records `VIEWED` and redirects to the real public job listing; it does not mark the job as applied. Click **Save application** when you want to track it.
8. Open `http://localhost:8080/applications` to move applications through allowed statuses, refresh deterministic follow-ups, and complete reminders.

Swagger UI is `http://localhost:8080/swagger-ui.html`. **Candidate profile** documents resume upload, current profile, the skill catalog, and reviewed-skill replacement. **Find jobs** runs and inspects the complete search pipeline. **Discovered source boards** lists Greenhouse boards found for this browser workspace and their `DISCOVERED`, `VALIDATED`, or `FAILED` status. **Applications** and **Follow-ups** document the same ownership-safe operations exposed in the Thymeleaf pages. Swagger sends the browser workspace cookie with each request.

## What each batch does

`findJobsJob` is the normal user flow: discovery from Adzuna and configured Jooble (including safe Greenhouse board enrichment when an official URL is exposed), normalization, skills, exact/fuzzy duplicate analysis, and workspace candidate scoring in six ordered steps. `jobDiscoveryJob` and `jobIntelligenceJob` remain separately launchable operator jobs. `searchProfileImportJob` preserves the original CSV learning workflow. `applicationFollowUpJob` creates candidate-scoped reminders for active applications; its identifying state revision changes only when that candidate's application history changes. `weeklyMarketInsightJob` creates shared market counts and salary aggregates.

Each batch returns a `jobExecutionId`. `COMPLETED` means the work finished. `FAILED` means inspect the execution and restart it when appropriate. Sending the same identifying parameters again returns a conflict because Spring Batch protects completed JobInstances.

## Operator CSV import

This is the original Spring Batch learning path, not the first-time browser workflow. Swagger endpoint: `POST /api/batch/search-profiles/import`.

`inputFile` is the readable CSV path seen by the application. `businessDate` is the ISO date used to identify this logical run. `failOnRow` is optional and only for restart testing; value `3` intentionally fails on data row 3. A normal example is:

```bash
curl -X POST 'http://localhost:8080/api/batch/search-profiles/import?inputFile=/Users/ankitkumar/IdeaProjects/joblens/data/import/search-profiles.csv&businessDate=2026-08-30'
```

When the app runs in Docker, a Mac path is not visible inside the container unless it is mounted. For this job, either run the app with `./mvnw spring-boot:run`, or add a Compose volume mapping and use the container path. Resume upload works through multipart HTTP because the file is sent directly to the app.

```bash
curl http://localhost:8080/actuator/health
```

Jobs are disabled at startup and must be launched explicitly.

## 1. Import search profiles

Sample input: `data/import/search-profiles.csv`.

```bash
curl -X POST \
  'http://localhost:8080/api/batch/search-profiles/import?inputFile=data/import/search-profiles.csv&businessDate=2026-08-29'
```

Required parameters are `inputFile` (readable CSV path) and `businessDate` (ISO date). Optional `failOnRow` injects a controlled failure at a data row (2 or greater).

The response returns `jobExecutionId`, `jobInstanceId`, job name, status, timestamps, and parameters. The job is chunk-oriented and uses PostgreSQL `ON CONFLICT (profile_id) DO UPDATE`.

Verify:

```bash
docker compose exec -T postgres psql -U joblens -d joblens \
  -c "select profile_id, source, keywords, location, include_skills, exclude_skills, employment_type, active from search_profile order by profile_id;" \
  -c "select input_file, row_number, rejection_reason, job_execution_id from search_profile_rejection order by created_at;"
```

## 2. Discover public jobs

Set credentials in `.env`:

```env
ADZUNA_APP_ID=your-app-id
ADZUNA_APP_KEY=your-app-key
```

Get credentials from [developer.adzuna.com](https://developer.adzuna.com/), restart the app, then run one profile:

```bash
curl -X POST \
  'http://localhost:8080/api/batch/discovery/run?businessDate=2026-08-29&profileId=SP001'
```

Or all active profiles:

```bash
curl -X POST \
  'http://localhost:8080/api/batch/discovery/run?businessDate=2026-08-29'
```

Adzuna is the default broad source. Optionally add a Singapore regional Jooble key in `.env`:

```env
# Get the key from https://sg.jooble.org/api/about
JOOBLE_API_KEY=your-singapore-regional-key
```

Recreate the Compose app (or restart a locally run app), then save and confirm preferences again. That projects a Jooble search profile alongside Adzuna; it is not created when the key is absent, so a normal Find-jobs run stays runnable. Jooble's documented free plan has a request quota; keep page limits modest. Its API supplies listing snippets, source links, and update timestamps, which JobLens preserves and normalizes. Live Singapore acceptance completed on 2026-08-31 with 60 Jooble records fetched, normalized, sighted, and scored in one completed Find Jobs execution.

Without the relevant credentials, startup still works but a direct live discovery launch fails observably. Mocked Adzuna and Jooble behavior is covered by tests.

Discovery stores one `source_fetch_run` per profile/execution and untouched individual provider JSON in `raw_job_posting`. Raw postings are idempotent by `(source, external_job_id)`; changed payloads reset processing to `NEW`.

## 3. Normalize, detect duplicates, extract skills, and score

```bash
curl -X POST \
  'http://localhost:8080/api/batch/intelligence/run?businessDate=2026-08-29'
```

The pipeline is:

```text
jobNormalizationStep: raw NEW/FAILED → normalized_job
skillExtractionStep: normalized_job → canonical job_skill rows
exactDuplicateDetectionStep: normalized_job → exact clusters, memberships, and evidence
fuzzyDuplicateDetectionStep: non-exact candidate pairs → explainable similarity suggestions
scoringStep:         job + skills + default candidate → score and reasons
```

Optional development failure injection:

```bash
curl -X POST \
  'http://localhost:8080/api/batch/intelligence/run?businessDate=2026-08-30&failAfterItems=3'
```

Use `failDuplicateDetection=true` or `failFuzzyDetection=true` to demonstrate transactional rollback and restart of the corresponding duplicate step. Failure-injection parameters are non-identifying.

Identifying parameters are `businessDate`, `normalizationVersion=v1`, and `duplicateDetectionVersion=fuzzy-v1`. Current score weights are Technical 40, Domain 15, Seniority 10, Location/work 10, Employment 10, Salary 10, Freshness 5. Persisted reason points must sum to the total score.

Inspect derived data:

```bash
docker compose exec -T postgres psql -U joblens -d joblens \
  -c "select alias_name, s.canonical_name from skill_alias a join skill s on s.id=a.skill_id order by alias_name;" \
  -c "select n.title, s.canonical_name, js.mention_count from job_skill js join normalized_job n on n.id=js.normalized_job_id join skill s on s.id=js.skill_id order by n.id,s.canonical_name;" \
  -c "select n.title, sc.total_score, sc.technical_score, sc.domain_score, sc.seniority_score, sc.location_score, sc.employment_score, sc.salary_score, sc.freshness_score from job_score sc join normalized_job n on n.id=sc.normalized_job_id order by sc.total_score desc;"
```

## 4. Inspect exact duplicates

The landing-table unique key `(source, external_job_id)` resolves repeated sightings of the same provider job to one raw and normalized identity. The duplicate step then connects distinct normalized jobs when either their source/external identity or `normalized_content_hash` is exactly equal. With the current landing constraint, distinct cluster members normally match by normalized hash; both evidence types are persisted and the algorithm supports their transitive closure.

Only groups with at least two members are stored. The lowest normalized-job ID is the deterministic canonical member.

```bash
curl http://localhost:8080/api/duplicates
curl http://localhost:8080/api/duplicates/1
curl http://localhost:8080/api/jobs/1
```

Verify clusters, memberships, and explainable pair evidence:

```bash
docker compose exec -T postgres psql -U joblens -d joblens \
  -c "select id,cluster_key,canonical_job_id,member_count from duplicate_cluster order by id;" \
  -c "select cluster_id,normalized_job_id,is_canonical from duplicate_cluster_member order by cluster_id,normalized_job_id;" \
  -c "select cluster_id,left_job_id,right_job_id,evidence_type,evidence_value from duplicate_match_evidence order by cluster_id,left_job_id,right_job_id;"
```

## 5. Inspect fuzzy duplicate suggestions

Exact-cluster pairs are excluded. Remaining pairs are cheaply blocked by title tokens, title trigrams, or exact company, then scored deterministically across title (40), description (25), company (20), location (10), and employment type (5). Missing optional dimensions are excluded from the effective weight. Scores at least 75 are stored as `POSSIBLE_DUPLICATE`; scores at least 90 are `LIKELY_DUPLICATE`.

These are explainable review suggestions, not hidden-employer claims, probabilities, or automatic merges. Thresholds are configurable with `JOBLENS_FUZZY_MINIMUM_SCORE` and `JOBLENS_FUZZY_LIKELY_SCORE`.

```bash
curl 'http://localhost:8080/api/duplicates/similarities?minimumScore=75'
curl http://localhost:8080/api/duplicates/similarities/1
curl http://localhost:8080/api/jobs/1
```

```bash
docker compose exec -T postgres psql -U joblens -d joblens \
  -c "select left_job_id,right_job_id,algorithm_version,overall_score,title_score,description_score,company_score,location_score,employment_score,decision,explanation from job_similarity order by overall_score desc;"
```

The duplicate subsystem keeps complex/reused statements under `src/main/resources/sql/` and calls them through small repositories backed by `NamedParameterJdbcTemplate`. This preserves SQL as SQL while retaining Spring JDBC and explicit transaction boundaries.

## 6. Track applications and generate follow-ups

The normal browser flow is `http://localhost:8080/applications`. Save a ranked job from the dashboard, select one of its allowed next statuses, then click **Refresh follow-ups**. Follow-up generation is a candidate-scoped, restartable Spring Batch job. The REST examples below expose the same domain behavior for inspection and automation.

Create one application for the default candidate and a normalized job:

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"normalizedJobId":1,"effectiveDate":"2026-08-20","note":"Saved for review"}' \
  http://localhost:8080/api/applications
```

Lifecycle transitions are audited and forward-only:

```text
SAVED → APPLIED → SCREENING → INTERVIEW → OFFER → ACCEPTED
          └────────── each active stage may exit to REJECTED or WITHDRAWN
```

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"status":"APPLIED","effectiveDate":"2026-08-21","note":"Applied online"}' \
  http://localhost:8080/api/applications/1/transitions

curl -X POST \
  'http://localhost:8080/api/batch/follow-ups/run?businessDate=2026-08-29'
```

`follow-up-v1` generates `APPLICATION_CHECK_IN` after 7 days, `RECRUITER_CHECK_IN` after 5, `INTERVIEW_THANK_YOU` after 1, and `OFFER_DECISION` after 3. A new transition cancels obsolete open follow-ups; completed rows remain historical. Reruns preserve unchanged rows and timestamps.

```bash
curl http://localhost:8080/api/applications
curl http://localhost:8080/api/applications/1
curl 'http://localhost:8080/api/follow-ups?status=OPEN&dueOnOrBefore=2026-08-29'
curl -X POST 'http://localhost:8080/api/follow-ups/1/complete?completedOn=2026-08-29'
```

```bash
docker compose exec -T postgres psql -U joblens -d joblens \
  -c "select id,normalized_job_id,status,status_effective_date,applied_on from job_application order by id;" \
  -c "select application_id,from_status,to_status,effective_date,note from application_status_history order by id;" \
  -c "select application_id,follow_up_type,due_date,status,completed_on from application_follow_up order by id;"
```

## View results

```bash
curl http://localhost:8080/api/jobs
curl http://localhost:8080/api/jobs/1
curl http://localhost:8080/api/duplicates
curl http://localhost:8080/api/duplicates/similarities
curl http://localhost:8080/api/applications
curl http://localhost:8080/api/follow-ups
curl http://localhost:8080/api/batch/executions
curl 'http://localhost:8080/api/market-insights?from=2026-01-01&to=2026-12-31'

# Interactive API documentation
open http://localhost:8080/swagger-ui.html
open http://localhost:8080/dashboard
```

The job detail endpoint returns normalized fields, canonical skills, score categories, score reasons, exact-cluster membership, and fuzzy similarity matches. The Thymeleaf dashboard is available at `/dashboard`.

Dashboard job rows include **Open on ADZUNA**, **JOOBLE**, or **GREENHOUSE**. Clicking records the job as viewed for this workspace and redirects to the original listing. Viewing does not create an application or mark a job as applied. The separate **Search more job portals** panel has preference-filled outbound searches for LinkedIn, JobStreet Singapore, SEEK Australia, and SEEK New Zealand; those portal results are not scraped, imported, or scored by JobLens. Inspect view history with `GET /api/job-views`.

Inspect automatically detected Greenhouse boards for the current browser workspace:

```bash
curl http://localhost:8080/api/source-boards
```

JobLens only accepts direct `https://job-boards.greenhouse.io/{board}` or legacy `https://boards.greenhouse.io/{board}` URLs (including the official embed form). It does not follow arbitrary Adzuna tracking redirects, which avoids treating an untrusted URL as an outbound fetch target. Current Adzuna payloads expose Adzuna tracking URLs, so this enrichment activates when a source directly provides a Greenhouse-hosted URL or a future legitimate adapter does.

## Batch history and restart

```bash
docker compose exec -T postgres psql -U joblens -d joblens \
  -c "select job_instance_id, job_name from batch_job_instance order by job_instance_id desc limit 10;" \
  -c "select job_execution_id, job_instance_id, status, start_time, end_time, exit_code from batch_job_execution order by job_execution_id desc limit 10;" \
  -c "select step_name, status, read_count, write_count, read_skip_count, write_skip_count, process_skip_count, commit_count, rollback_count from batch_step_execution order by step_execution_id desc limit 20;"
```

On failure, committed chunks remain committed. A restart creates a new JobExecution for the same JobInstance and resumes from ExecutionContext checkpoint state. Derived writes are idempotent.

## Tests

Docker is required for PostgreSQL Testcontainers tests; source-adapter tests use MockWebServer.

```bash
set -a; source .env; set +a
./mvnw clean test
```

Focused suites:

```bash
./mvnw -Dtest=JobIntelligenceIntegrationTests test
./mvnw -Dtest=JobDiscoveryIntegrationTests test
./mvnw -Dtest=SearchProfileImportJobIntegrationTests test
```

## Troubleshooting

If PostgreSQL authentication fails, ensure Compose and the app use the same `JOBLENS_DB_PASSWORD` (local default: `joblens-local`) and restart the app. If the dashboard is empty, confirm the setup profile and click **Find and rank jobs**. If Adzuna reports missing credentials, set `ADZUNA_APP_ID` and `ADZUNA_APP_KEY` in `.env` and recreate the app container. To enable Jooble, set a Singapore regional `JOOBLE_API_KEY`, recreate/restart the app, then save and confirm preferences again. Greenhouse GET access needs no API key; JobLens validates discovered boards internally, and their status is visible at `/api/source-boards`.

## Interview/demo runbook

1. Start the stack: `docker compose up -d` and confirm `docker compose ps` reports both services healthy/running.
2. Open `/setup`, upload a resume, save preferences, and confirm the versioned profile.
3. Open `/dashboard`, click **Find and rank jobs**, then show the six StepExecutions through `/api/batch/executions`.
4. Open `/swagger-ui.html`; demonstrate **Find jobs**, `/api/jobs`, and exact/fuzzy duplicate inspection.
5. Save a ranked job, open `/applications`, transition it to `APPLIED`, refresh follow-ups, and complete one reminder.
6. Run market insights for a Monday week start and inspect `/api/market-insights`.
7. Show restartability with `/api/batch/executions` and the Batch metadata SQL queries above.
8. Tear down with `docker compose down` (add `-v` only when intentionally deleting local database data).

## Optional future extensions

The anonymous, manual-use product flow is complete. These are separate product choices, not unfinished parts of the current workflow:

- Store original resume bytes through an object-storage adapter; V11 currently stores validated metadata and SHA-256 only.
- Add optional schedules/notifications after the manual one-click workflow is proven useful.
- Add more legitimate source adapters only when a candidate-facing public search API exists or a commercial agreement explicitly authorizes this use. The current LinkedIn and SEEK/JobStreet APIs are partner/hirer integrations for posting and applications, not public candidate-job discovery; dashboard links provide direct searches instead. LinkedIn and Indeed scraping remain prohibited.
- Add login/account recovery only if anonymous browser-cookie workspaces need cross-device persistence.
- Calibrate deterministic scoring and fuzzy thresholds against reviewed real examples.

The latest full verification evidence is recorded in `BUILD_PROGRESS.md`.
