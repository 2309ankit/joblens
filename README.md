# JobLens

JobLens is a production SaaS product for explainable job-market intelligence. **V1** is the first
release line being built and maintained; its current development artifact is `1.0.0-SNAPSHOT`.
The current repository is a batch-first modular-monolith implementation: an anonymous browser workspace can upload and validate a
resume, review skill/title evidence, select search intent, discover legitimate public postings, rank
candidate-visible jobs, and track applications. Anonymous identity and the local Compose topology are
verified development foundations, not the target production boundary.

The authoritative startup requirements and fixed/not-fixed assessment are in
[PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md). New sessions start with
[SESSION_HANDOFF.md](SESSION_HANDOFF.md), architecture decisions are in
[SYSTEM_DESIGN.md](SYSTEM_DESIGN.md), selectable work is in
[NEXT_MILESTONES.md](NEXT_MILESTONES.md), mandatory development and review gates are in
[ENGINEERING_STANDARDS.md](ENGINEERING_STANDARDS.md), and historical evidence remains in
[BUILD_PROGRESS.md](BUILD_PROGRESS.md).

## Architecture

Current development topology: one Spring Boot application, one PostgreSQL database, and one process.
The startup target keeps one modular codebase but permits independently scaled API and worker runtime
roles:

```text
Browser cookie → workspace → validated resume draft → confirmed candidate/preferences
                                                   ↓
                    findJobsJob
                    ├─ jobDiscoveryStep → JobSourceClient registry
                    │                    ├─ Adzuna API
                    │                    ├─ Jooble Search API (when configured)
                    │                    ├─ exposed Greenhouse URL → internal board registry → public Job Board API
                    │                    └─ exposed Lever URL → internal board registry → public Postings API
                    │                    → raw_job_posting/workspace_job_sighting
                    ├─ jobNormalizationStep → normalized_job
                    ├─ skillExtractionStep   → job_skill
                    ├─ exactDuplicateDetectionStep → duplicate_cluster/membership/evidence
                    ├─ fuzzyDuplicateDetectionStep → job_similarity
                    └─ scoringStep            → best job_score + per-role score/reason evidence

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

Flyway migrations are incremental. V1-V10 build the original Batch, intelligence, lifecycle, insights, and view-tracking slices; V11 adds anonymous workspace onboarding; V12 adds workspace discovery, source projections, job sightings, and Find-jobs run history; V13 adds safe automatic company-board discovery; V14 adds the optional Jooble source; V15 adds immutable per-source run observability; V16 adds Lever; V17 normalizes multiple workspace search markets; V18 adds categorized inclusive skill/role taxonomy, workspace-private additions, and versioned suggestion evidence; V19 makes custom-skill reference cleanup follow workspace deletion; V20 adds versioned ESCO taxonomy releases and uncatalogued-term review artifacts; V21 adds versioned resume-readability assessments, stable findings, and acknowledgement state; V22 separates ordered target-role intent from résumé evidence and persists versioned generated provider queries per market; V23 adds versioned role-calibration overlays and per-role score evidence.

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
JOOBLE_COUNTRY_CODE=sg
```

Environment variables override these values. Never commit real credentials; `.env` is ignored.

## First-time use

1. Open `http://localhost:8080/setup`. JobLens creates an anonymous workspace cookie in this browser.
2. (Recommended) import the pinned ESCO release once: `curl -X POST 'http://localhost:8080/api/batch/taxonomy/esco/import?taxonomyVersion=1.2.1'`. The import is a restartable, idempotent Batch job; until it completes, the curated JobLens seed remains the fallback catalogue.
3. Upload a PDF, DOC, or DOCX resume, maximum 5 MB. Apache Tika extracts text. Unsupported, corrupt, oversized, or unreadable files fail. A readable but unusual resume, job description, or interview-style document is preserved as `REVIEW_REQUIRED` with evidence and must be acknowledged before activation. JobLens stores metadata, hash, assessment measures, and bounded evidence—not the original file bytes or full extracted text.
4. After JobLens reads the resume, inspect the suggested skill and role chips. Suggestions remain
   editable evidence until activation: remove mistakes, add a catalogue or private workspace term,
   and choose one to three ordered target roles. Preferred sectors are optional.
5. Review **Where you live now** separately from **Places you want to search**. A new profile starts
   with a labelled, editable browser time-zone/language estimate and a visible Singapore fallback;
   **Use browser estimate** can rerun it. Add, edit, or remove up to ten supported country and
   city/region market rows. ISO alpha-2 codes remain internal and every market runs independently.
   Page count, employment type, work arrangement, and the optional provider-query override are under
   **Advanced search preferences**; the displayed defaults work without opening that section.
6. Click **Activate profile** once. This versions the reviewed skills and preferences together,
   stores the `role-intent-v1` query plan for inspection, and activates one runnable source profile
   per generated query and market. Each has its own pagination and restart checkpoint. JobLens
   searches Adzuna for every supported market and Jooble only for the regional country configured by
   `JOOBLE_COUNTRY_CODE`. Direct official Greenhouse or Lever URLs are validated and searched without
   ATS credentials.
7. Open `http://localhost:8080/dashboard` and click **Find and rank jobs**. This runs discovery through scoring as one restartable Spring Batch Job.
   The **Latest source run** panel then shows each source's status, attempted/fetched pages, received and
   new/changed/unchanged records, raw/normalized/sighted/scored totals, and any safe failure reason.
   If a later source fails, earlier results remain available and the panel reports `PARTIAL` with a
   **Restart failed run** button.
7. Open a result with its source link. This records `VIEWED` and redirects to the real public job listing; it does not mark the job as applied. Click **Save application** when you want to track it.
8. Open `http://localhost:8080/applications` to move applications through allowed statuses, refresh deterministic follow-ups, and complete reminders.

Swagger UI is `http://localhost:8080/swagger-ui.html`. **Candidate profile** documents resume upload, current profile, normalized search preferences, searchable skill and role catalogues, integrated country capabilities, explainable profile/readiness findings, acknowledgement, and reviewed-skill replacement. Relevant inspection endpoints include `GET /api/candidate-profile/intelligence`, `GET /api/candidate-profile/readiness`, `POST /api/candidate-profile/readiness/acknowledgement`, `GET /api/candidate-profile/search-queries`, `/skills/catalog?query=...`, `/roles/catalog?query=...`, and `/countries`. **Find jobs** runs and inspects the complete search pipeline. Swagger sends the browser workspace cookie with each request.

Profile matching loads the applicable PostgreSQL catalogue and aliases once, then uses deterministic
case-insensitive token-boundary matching in memory. Flyway seeds a cross-discipline starter taxonomy;
user-created skills and roles are visible only inside their workspace. Custom skills are compared
directly with job text for that candidate, so they can contribute to the universal confirmed-skill
dimension without becoming global extraction terms. Title confidence is a transparent ordering
heuristic (headline 0.950, recent experience 0.900, other resume body 0.600), not a probability or an
employment claim.

Resume readiness uses deterministic `readability-v1` findings for contact details, standard
sections, job-title lines, employment dates, education, parsing quality, length, document-type
uncertainty, and DOCX layout markup that can be measured reliably. Its 0-100 score is not candidate
quality, employability, job fit, or a proprietary ATS score. The source boundary and exact deductions
are documented in [ATS_READINESS.md](ATS_READINESS.md). Keyword alignment remains deliberately
separate because it requires an explicit target role or job description.

The integrated country catalogue is an explicit snapshot of markets supported by JobLens's Adzuna
adapter, plus the configured Jooble regional market when credentials are present. The provider API
shape uses Adzuna's documented `jobs/{country}/search/{page}` route; see the official
[Adzuna API overview](https://developer.adzuna.com/overview) and
[search documentation](https://developer.adzuna.com/docs/search). Unsupported ISO codes are rejected
before profile activation rather than producing a knowingly unrunnable source profile.

## What each batch does

`findJobsJob` is the normal user flow: independent discovery for every confirmed source/market profile, including safe Greenhouse and Lever board enrichment when direct official URLs are exposed; normalization; skills; exact/fuzzy duplicate analysis; and workspace candidate scoring in six ordered steps. The `universal-v1` policy evaluates each job against every selected target role, then projects the highest role score while retaining all per-role reasons. Frontend, Backend Engineering, AI/ML, and Sales/Customer Success add versioned calibrated title/skill evidence; every other role keeps the same universal title, confirmed-skill, sector, seniority, location/work, employment, salary, and freshness dimensions. Missing calibrated skills lower evidence points but never discard a job. `jobDiscoveryJob` and `jobIntelligenceJob` remain separately launchable operator jobs. `searchProfileImportJob` remains the legacy/operator CSV import. `applicationFollowUpJob` creates candidate-scoped reminders for active applications; its identifying state revision changes only when that candidate's application history changes. `weeklyMarketInsightJob` creates shared market counts and salary aggregates.

The normal Find Jobs flow returns a workspace-owned product `runId`, `status`, and `outcome`.
`ACTIVE` means the same command is already progressing, `FAILED` can be restarted, and `STALE` means
an orphaned execution crossed the configured recovery threshold. Operator-only batch launches and
execution history continue to use internal `jobExecutionId` values. Sending the same completed
identifying parameters again remains protected by Spring Batch.

Find-jobs source health is also available in Swagger or with curl. The detail endpoint accepts only a
run owned by the current browser workspace cookie:

```bash
curl -c joblens-cookie.txt -b joblens-cookie.txt \
  http://localhost:8080/api/batch/find-jobs/runs

curl -c joblens-cookie.txt -b joblens-cookie.txt \
  http://localhost:8080/api/batch/find-jobs/runs/{runId}

curl -X POST -c joblens-cookie.txt -b joblens-cookie.txt \
  http://localhost:8080/api/batch/find-jobs/runs/{runId}/restart
```

`PARTIAL` is a JobLens inspection outcome. It means at least one source completed or returned an
honest empty result before another source failed. Committed source results remain visible, and a
restart resumes the unfinished source checkpoint before continuing normalization and scoring. Each
source summary reports its generated query and reconciled provider/raw/normalized/sighted/scored
counts; an empty run identifies the first zero stage. Stored and displayed failures are bounded and
redacted; credentials and provider response bodies are not retained in run errors. The orphan
threshold defaults to 30 minutes and can be configured with `JOBLENS_FIND_JOBS_STALE_AFTER`.

## Operator CSV import

This is the legacy/operator CSV import path, not the first-time browser workflow. Swagger endpoint:
`POST /api/batch/search-profiles/import`.

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
ADZUNA_MAX_DAYS_OLD=30
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
JOOBLE_COUNTRY_CODE=sg
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
scoringStep:         job + skills + selected roles → universal/overlay scores and reasons
```

Optional development failure injection:

```bash
curl -X POST \
  'http://localhost:8080/api/batch/intelligence/run?businessDate=2026-08-30&failAfterItems=3'
```

Use `failDuplicateDetection=true` or `failFuzzyDetection=true` to demonstrate transactional rollback and restart of the corresponding duplicate step. Failure-injection parameters are non-identifying.

Identifying parameters are `businessDate`, `normalizationVersion=v1`,
`duplicateDetectionVersion=fuzzy-v1`, and `rankingPolicyVersion=universal-v1` (plus workspace,
candidate, and search-definition identity in Find Jobs). Current universal weights are role title 25,
confirmed/calibrated skills 15, optional sector 15, seniority 10, location/work arrangement 10,
employment 10, salary availability 10, and freshness 5. The compatibility `technical_score` projection
is title plus skills and `domain_score` is sector. Persisted reason points must sum to the total score.

Inspect derived data:

```bash
docker compose exec -T postgres psql -U joblens -d joblens \
  -c "select alias_name, s.canonical_name from skill_alias a join skill s on s.id=a.skill_id order by alias_name;" \
  -c "select n.title, s.canonical_name, js.mention_count from job_skill js join normalized_job n on n.id=js.normalized_job_id join skill s on s.id=js.skill_id order by n.id,s.canonical_name;" \
  -c "select n.title, sc.best_target_role_name, sc.ranking_policy_version, sc.calibration_pack_code, sc.calibration_pack_version, sc.total_score from job_score sc join normalized_job n on n.id=sc.normalized_job_id order by sc.total_score desc;" \
  -c "select target_role_name, policy_version, total_score, title_score, skill_score, sector_score, seniority_score, location_score, employment_score, salary_score, freshness_score from job_role_score order by normalized_job_id,total_score desc;"
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

The job detail endpoint returns normalized fields, canonical skills, the best target role, ranking and
overlay versions, every per-role score with point reasons, exact-cluster membership, and fuzzy
similarity matches. The Thymeleaf dashboard names the best role/overlay and is available at
`/dashboard`.

Dashboard job rows include **Open on ADZUNA**, **JOOBLE**, **GREENHOUSE**, or **LEVER**. Clicking records the job as viewed for this workspace and redirects through the exact listing URL supplied by that provider. Adzuna discovery requests date-sorted postings no more than `ADZUNA_MAX_DAYS_OLD` days old (default 30), and older landed Adzuna rows are excluded from dashboard/API lists. The latest-run table names the source and market, such as **ADZUNA — India (IN)**, so parallel country runs are distinguishable. Viewing does not create an application or mark a job as applied. The separate **Search more job portals** panel creates three explainable queries for each selected market. LinkedIn is generated for every market; JobStreet appears for Singapore, SEEK Australia for `AU`, and SEEK New Zealand for `NZ`. Unselected regional links are not shown. These portal results are not scraped, imported, or scored by JobLens. Inspect view history with `GET /api/job-views`.

Inspect automatically detected company boards for the current browser workspace:

```bash
curl http://localhost:8080/api/source-boards
```

Greenhouse detection accepts direct `https://job-boards.greenhouse.io/{board}` or legacy `https://boards.greenhouse.io/{board}` URLs, including the official embed form. Lever detection accepts only direct global `https://jobs.lever.co/{site}/...` URLs and reads that site through the documented public [Lever Postings API](https://github.com/lever/postings-api). The current adapter deliberately does not accept tracking redirects, lookalike hosts, or the separate EU Lever host. Board enrichment activates only when an integrated source directly exposes one of these official URLs. Both APIs are public GET interfaces and need no employer or applicant credential.

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

If PostgreSQL authentication fails, ensure Compose and the app use the same `JOBLENS_DB_PASSWORD` (local default: `joblens-local`) and restart the app. If the dashboard is empty, activate the setup profile and click **Find and rank jobs**. If a document is rejected as not being a resume, upload the candidate's actual career resume with contact details and normal resume sections rather than a vacancy or interview specification. If Adzuna reports missing credentials, set `ADZUNA_APP_ID` and `ADZUNA_APP_KEY` in `.env` and recreate the app container. To enable Jooble, set its regional `JOOBLE_API_KEY` and matching `JOOBLE_COUNTRY_CODE`, recreate/restart the app, then activate the profile again. Greenhouse and Lever GET access needs no API key; JobLens validates discovered boards internally, and their status is visible at `/api/source-boards`.

## V1 development review runbook

1. Start the stack: `docker compose up -d` and confirm `docker compose ps` reports both services healthy/running.
2. Open `/setup`, upload a resume, then review and activate skills and preferences with the single combined action.
3. Open `/dashboard`, click **Find and rank jobs**, then show the six StepExecutions through `/api/batch/executions`.
4. Open `/swagger-ui.html`; demonstrate **Find jobs**, `/api/jobs`, and exact/fuzzy duplicate inspection.
5. Save a ranked job, open `/applications`, transition it to `APPLIED`, refresh follow-ups, and complete one reminder.
6. Run market insights for a Monday week start and inspect `/api/market-insights`.
7. Show restartability with `/api/batch/executions` and the Batch metadata SQL queries above.
8. Tear down with `docker compose down` (add `-v` only when intentionally deleting local database data).

## Startup readiness gaps

The current product flow is the verified JobLens V1 development baseline, not a completed public product. The authoritative
status matrix and launch gates are in [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md). Major gaps
include authenticated ownership/RBAC, shared provider-budgeted ingestion, product-level asynchronous
run control and live progress, secure résumé storage and privacy workflows, production delivery and
observability, HA/backups/restore evidence, load/security testing, and the remaining product defects
recorded in `SESSION_HANDOFF.md`.

**S0.1 Discovery Execution Safety completed on 2026-09-08.** Its controlled AI Engineer and broad-role
evidence, empty-provider diagnostics, safe simultaneous-command behavior and stale-run recovery are
recorded in [BUILD_PROGRESS.md](BUILD_PROGRESS.md). The assisted multi-market onboarding follow-up
completed on 2026-09-08, and S0.2 Onboarding Correctness was selected on 2026-09-09. S0.3 and S1–S6
remain separate checkpoint decisions. Authenticated ownership (S1) is still mandatory before private
beta or public launch.

- Store original resume bytes through an encrypted, scanned object-storage lifecycle; V11 currently stores validated metadata and SHA-256 only.
- Add notification delivery only with user preferences, quiet hours, retries, and idempotency.
- Add more legitimate source adapters only when a candidate-facing public search API exists or a commercial agreement explicitly authorizes this use. The current LinkedIn and SEEK/JobStreet APIs are partner/hirer integrations for posting and applications, not public candidate-job discovery; dashboard links provide direct searches instead. LinkedIn and Indeed scraping remain prohibited.
- Replace anonymous cookie ownership with authenticated accounts, recovery, authorization, and audited support access before public launch.
- Complete S4 Job Explorer filtering/keyset pagination, then collect private Fit/Maybe/Not-fit labels
  to measure Precision@10 and revise the initial deterministic overlay signals from evidence. The
  former M3.3/M3.4 labels are retained only as history.

The latest full verification evidence is recorded in `BUILD_PROGRESS.md`.
