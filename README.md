# JobLens

JobLens is a modular Spring Boot application for personal job-market intelligence. It imports search profiles, discovers public Adzuna postings, stores raw JSON, normalizes and scores jobs, detects duplicates, and tracks applications with generated follow-ups.

## Architecture

One Spring Boot application, one PostgreSQL database, one deployable process:

```text
CSV → searchProfileImportJob → search_profile
                              ↓
                    jobDiscoveryJob → Adzuna → source_fetch_run/raw_job_posting
                                                        ↓
                    jobIntelligenceJob
                    ├─ jobNormalizationStep → normalized_job
                    ├─ skillExtractionStep   → job_skill
                    ├─ exactDuplicateDetectionStep → duplicate_cluster/membership/evidence
                    ├─ fuzzyDuplicateDetectionStep → job_similarity
                    └─ scoringStep            → job_score/job_score_reason

                    applicationFollowUpJob
                    └─ applicationFollowUpGenerationStep → application_follow_up
```

Packages:

```text
batchapi       REST launch, history, and job-read endpoints
searchprofile  CSV reader, validation, rejection, and upsert
discovery      JobSourceClient, Adzuna client, pagination, raw landing
intelligence   normalization, skills, duplicate detection, candidate profile, and scoring
lifecycle      application transitions, history, and follow-up generation
```

Flyway migrations are incremental: V1 Batch metadata, V2 profile import, V3 discovery/raw landing, V4 normalized jobs, V5 skills/candidate/scoring, V6 exact duplicate clusters, V7 fuzzy similarities, and V8 application lifecycle/follow-ups.

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
```

Environment variables override these values. Never commit real credentials; `.env` is ignored.

## What each batch does

`searchProfileImportJob` reads a CSV file containing search profiles. It validates each row, saves valid profiles, and saves rejected rows with a reason. `jobDiscoveryJob` reads saved profiles, calls Adzuna, and stores raw provider responses. `jobIntelligenceJob` cleans raw jobs, extracts skills, finds exact/fuzzy duplicates, and calculates candidate-fit scores. `applicationFollowUpJob` creates reminders for active applications. `weeklyMarketInsightJob` creates weekly counts and salary aggregates.

Each batch returns a `jobExecutionId`. `COMPLETED` means the work finished. `FAILED` means inspect the execution and restart it when appropriate. Sending the same identifying parameters again returns a conflict because Spring Batch protects completed JobInstances.

## Search-profile CSV import

Swagger endpoint: `POST /api/batch/search-profiles/import`.

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

## 2. Discover Adzuna jobs

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

Without credentials, startup still works but a live discovery launch fails observably. Mocked Adzuna behavior is covered by tests.

Discovery stores one `source_fetch_run` per profile/execution and untouched individual Adzuna JSON in `raw_job_posting`. Raw postings are idempotent by `(source, external_job_id)`; changed payloads reset processing to `NEW`.

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

Dashboard job rows include **Open on ADZUNA**. Clicking it records the job as viewed and redirects to the original source listing. Viewing does not create an application or mark a job as applied. Inspect view history with `GET /api/job-views`.

## Batch history and restart

```bash
docker compose exec -T postgres psql -U joblens -d joblens \
  -c "select job_instance_id, job_name from batch_job_instance order by job_instance_id desc limit 10;" \
  -c "select job_execution_id, job_instance_id, status, start_time, end_time, exit_code from batch_job_execution order by job_execution_id desc limit 10;" \
  -c "select step_name, status, read_count, write_count, read_skip_count, write_skip_count, process_skip_count, commit_count, rollback_count from batch_step_execution order by step_execution_id desc limit 20;"
```

On failure, committed chunks remain committed. A restart creates a new JobExecution for the same JobInstance and resumes from ExecutionContext checkpoint state. Derived writes are idempotent.

## Tests

Docker is required for PostgreSQL Testcontainers tests; Adzuna tests use MockWebServer.

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

If PostgreSQL authentication fails, ensure Compose and the app use the same `JOBLENS_DB_PASSWORD` (local default: `joblens-local`) and restart the app. If `/api/jobs` is empty, run discovery or load raw development data, then run intelligence. If discovery reports missing credentials, set `ADZUNA_APP_ID` and `ADZUNA_APP_KEY`.

## Interview/demo runbook

1. Start the stack: `docker compose up -d` and confirm `docker compose ps` reports both services healthy/running.
2. Open `/dashboard` and `/swagger-ui.html`.
3. Import a CSV profile, then launch discovery and intelligence through the documented batch endpoints.
4. Demonstrate exact/fuzzy duplicate inspection with `/api/duplicates` and `/api/duplicates/similarities`.
5. Create an application, transition it to `APPLIED`, run follow-up generation, and complete one follow-up.
6. Run market insights for a Monday week start and inspect `/api/market-insights`.
7. Show restartability with `/api/batch/executions` and the Batch metadata SQL queries above.
8. Tear down with `docker compose down` (add `-v` only when intentionally deleting local database data).

## Remaining milestones

## Resume profile reader

Upload a PDF or DOCX resume; Apache Tika extracts text and deterministically matches skills from the database catalog:

```bash
curl -F 'file=@/path/to/resume.pdf' http://localhost:8080/api/candidate-profile/resume
```

The response includes `detectedSkills` and `reviewRequired=true`; review the profile before relying on new rankings.

Review profile at `http://localhost:8080/profile`, inspect with `GET /api/candidate-profile`, and save corrected skills with `PUT /api/candidate-profile` and body `{"skills":["Java","Spring Boot"]}`.

Implemented: PostgreSQL/Flyway/Batch metadata, profile import, Adzuna raw discovery, normalization, skills, candidate scoring, duplicate detection, application lifecycle, follow-up generation, restartability, and REST APIs.

All implementation milestones and the interview/demo runbook are complete. Live Adzuna verification is also complete when valid credentials are supplied through `.env`.
