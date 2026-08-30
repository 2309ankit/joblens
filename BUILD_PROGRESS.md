# JobLens Build Progress

## Project

**JobLens**

Production-style personal job-market intelligence platform built around Spring Batch.

## Current Environment

| Item           | Status      | Evidence                                                            |
| -------------- | ----------- | ------------------------------------------------------------------- |
| macOS          | COMPLETE    | Development machine verified                                        |
| Apple Silicon  | COMPLETE    | Architecture reported as `aarch64`                                  |
| Java 21        | COMPLETE    | Temurin 21.0.12.1 verified for the shell and Maven                  |
| Maven          | COMPLETE    | Maven Wrapper 3.9.16 verified on Java 21.0.12.1                     |
| Maven Wrapper  | COMPLETE    | `mvnw`, `mvnw.cmd`, and `.mvn/` present                             |
| Git            | COMPLETE    | Repository initialized on `main`; baseline commit exists            |
| IntelliJ IDEA  | COMPLETE    | Repository opened and accessible to Codex                           |
| Docker Desktop | COMPLETE    | Docker 29.7.2 server verified on `desktop-linux`                    |
| PostgreSQL     | COMPLETE    | PostgreSQL 17.11 container healthy; readiness and SQL verified      |

## Framework Baseline

* Java: 21
* Spring Boot: 4.1.1
* Build tool: Maven
* Packaging: Jar

### Spring Boot Version Deviation

The original JobLens specification requested a stable Spring Boot 3.x release.

The generated project currently uses:

`Spring Boot 4.1.1`

This is a deliberate recorded deviation.

Compatibility must be validated incrementally.

Do not change the Spring Boot version unless explicitly requested.

## Current Dependencies

The generated project currently contains:

* Spring Boot Actuator
* Spring Batch
* Flyway
* Spring JDBC
* Thymeleaf
* Validation
* Spring Web MVC
* PostgreSQL JDBC driver
* Spring Boot DevTools
* Spring Boot test starters for the selected components

## Required Build Milestones

| #  | Milestone                                        | Status      |
| -- | ------------------------------------------------ | ----------- |
| 1  | Environment verified                             | COMPLETE    |
| 2  | Spring Initializr project generated manually     | COMPLETE    |
| 3  | Generated application compiles and starts        | COMPLETE    |
| 4  | PostgreSQL starts through Docker Compose         | COMPLETE    |
| 5  | Flyway and Spring Batch metadata tables verified | COMPLETE    |
| 6  | CSV search-profile import job works              | COMPLETE    |
| 7  | Failed CSV import and restart demonstrated       | COMPLETE    |
| 8  | One real internet job source works               | COMPLETE    |
| 9  | Raw postings stored idempotently                 | COMPLETE    |
| 10 | API pagination and restart work                  | COMPLETE    |
| 11 | Normalization works                              | COMPLETE    |
| 12 | Skill extraction works                           | COMPLETE    |
| 13 | Duplicate detection works                        | COMPLETE    |
| 14 | Candidate scoring works                          | COMPLETE    |
| 15 | Lifecycle and follow-up generation work          | COMPLETE    |
| 16 | Weekly market insight works                      | COMPLETE    |
| 17 | Dashboard works                                  | COMPLETE    |
| 18 | Integration tests pass                           | COMPLETE    |
| 19 | Dockerized application works                     | COMPLETE    |
| 20 | README and interview demonstration complete      | COMPLETE    |
| 21 | Anonymous workspace onboarding works             | COMPLETE    |
| 22 | UI-managed source definitions work               | COMPLETE    |
| 23 | Adzuna, optional Jooble, and automatic Greenhouse enrichment work | COMPLETE    |
| 24 | One-click restartable Find jobs workflow works   | COMPLETE    |
| 25 | Workspace ownership and isolation work           | COMPLETE    |
| 26 | Workspace resume skill review works              | COMPLETE    |
| 27 | Lifecycle and follow-up Thymeleaf controls work  | COMPLETE    |

## Verified Evidence

### Java

Java 21 is installed and active for repository commands.

Verified shell runtime:

`Temurin 21.0.12.1`

Verified Maven runtime:

`Java version: 21.0.12.1, vendor: Eclipse Adoptium`

### Maven Compilation

The following command was executed during the configuration-baseline milestone:

```bash
./mvnw clean compile
```

Observed result:

Compilation completed successfully before the test application context started.

The compiler reported `release 21` and compiled the main and test sources.

### Configuration Baseline

The application configuration now externalizes PostgreSQL datasource settings through environment variables:

```properties
spring.datasource.url=${JOBLENS_DB_URL:jdbc:postgresql://localhost:5432/joblens}
spring.datasource.username=${JOBLENS_DB_USERNAME:joblens}
spring.datasource.password=${JOBLENS_DB_PASSWORD:}
spring.batch.job.enabled=false
```

`.env` is ignored by Git.

Verified with:

```bash
git check-ignore .env
```

Observed output:

```text
.env
```

`.env.example` exists with safe local PostgreSQL placeholder values.

`compose.yaml` defines PostgreSQL 17 with a named volume and readiness healthcheck. An ignored `.env` supplies local development settings.

Docker 29.7.2 and Compose v5.4.0 are installed. `docker info` verified the Docker Desktop server, and `docker compose ps` reported the PostgreSQL container healthy.

`pg_isready` reported accepting connections. A real `psql` query returned database `joblens`, user `joblens`, and PostgreSQL 17.11.

Flyway is the single schema owner. Migration `V1__create_spring_batch_metadata.sql` uses the Spring Batch 6.0.5 PostgreSQL schema. This is required because Spring Boot 4.1 no longer exposes the former `spring.batch.jdbc.initialize-schema` configuration.

Direct PostgreSQL inspection verified Flyway history version 1 and all six Spring Batch metadata tables.

### Git Diff Check

The following command was executed after the configuration-baseline edits:

```bash
git diff --check
```

Observed result: command completed successfully with no output.

### Git Baseline

The repository has been initialized with Git on branch:

`main`

Initial baseline commit:

`d5c013c - Initialize JobLens project`

This commit represents the generated application plus `AGENTS.md` before application configuration work begins.

## Latest Test Result

The following command was executed against the local PostgreSQL container:

```bash
./mvnw test
```

Observed result after the CSV import milestone: `BUILD SUCCESS`; 12 tests run, 0 failures, 0 errors.

The context test verified HikariCP connection creation, Flyway validation/migration, Batch repository bean creation, and application-context startup against PostgreSQL.

The application was also started with `./mvnw spring-boot:run`. Tomcat listened on port 8080 and `GET /actuator/health` returned:

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

## Current Repository State

Important existing files include:

```text
AGENTS.md
BUILD_PROGRESS.md
.env.example
compose.yaml
pom.xml
mvnw
mvnw.cmd
.mvn/
src/main/java/com/ankit/joblens/JoblensApplication.java
src/main/resources/application.properties
src/test/java/com/ankit/joblens/JoblensApplicationTests.java
```

Current `application.properties` contains:

```properties
spring.application.name=joblens
spring.datasource.url=${JOBLENS_DB_URL:jdbc:postgresql://localhost:5432/joblens}
spring.datasource.username=${JOBLENS_DB_USERNAME:joblens}
spring.datasource.password=${JOBLENS_DB_PASSWORD:}
spring.batch.job.enabled=false
```

The application now contains `searchProfileImportJob` and `searchProfileImportStep`.

Flyway migrations V1 and V2 are applied. V2 owns the `search_profile` and `search_profile_rejection` business tables.

No external job-source integrations have been implemented.

## Current Milestone

The anonymous manual-use workflow is complete. Flyway V11's existing profile-version and skill tables now support UI skill correction without another schema migration. Flyway V8's existing lifecycle tables now back Thymeleaf application and follow-up controls.

Documentation navigation is split by purpose: `SESSION_HANDOFF.md` is the concise resume point,
`NEXT_MILESTONES.md` is the selection index for future work, `README.md` is the user/operator runbook,
and this file remains the detailed evidence history.

`findJobsJob` executes discovery, normalization, skill extraction, exact duplicate detection, fuzzy duplicate analysis, and workspace candidate scoring as one restartable Spring Batch Job. Adzuna, optional Jooble, and Greenhouse sit behind `JobSourceClient`; provider JSON is stored before provider-specific normalization. Greenhouse is internally discovered and validated only from exposed official board URLs, rather than asking users for technical board tokens. Complex discovery and inspection SQL is externalized and uses `NamedParameterJdbcTemplate`.

## Next Observable Milestone

No implementation milestone is active. Optional product choices are original-resume object storage, schedules/notifications, verified additional public source APIs, optional login/cross-device recovery, and data-backed scoring calibration.

## Profile Review and Lifecycle UI Evidence

`/setup` displays the canonical skill catalog and replaces only the current workspace's DRAFT skills. Editing an ACTIVE profile forks a new draft; the active candidate scoring skills remain unchanged until confirmation. Invalid and empty selections are observable request errors. The obsolete global seeded-profile resume editor was removed so browser profile changes consistently use workspace ownership.

`/applications` lists candidate-owned applications, exposes only policy-approved next statuses, runs the candidate-scoped follow-up Batch job, and completes owned reminders. Dashboard rows can save a discovered job or open its existing application. `applicationFollowUpJob` accepts an identifying candidate ID and deterministic application-history revision; unchanged state maps to the same completed JobInstance, while a new status history event creates a new JobInstance. Legacy direct operator launches without a candidate parameter retain global behavior.

Focused PostgreSQL Testcontainers evidence covers draft-before-activation skill edits, catalog validation, workspace isolation, candidate-scoped follow-up generation, no-change idempotency, changed-state reruns, failure rollback, and same-JobInstance restart. REST contract tests cover successful and invalid reviewed-skill updates.

Earlier verification on 2026-08-31: `./mvnw clean test` completed with 62 tests, 0 failures, 0 errors, and 0 skipped. The rebuilt Compose app returned health `200`; a fresh workspace received `302 /setup` from `/applications`; Swagger exposed the new profile and lifecycle descriptions; and a controlled candidate workspace rendered `/applications` with HTTP 200. The exact temporary workspace was removed after the render check.

## Workspace Onboarding and Find Jobs Evidence

`WorkspaceOnboardingIntegrationTests` proves two anonymous workspaces create independent candidate profiles and source projections, and that revising an active profile creates a new draft before superseding the prior version. `FindJobsIntegrationTests` proves all six steps complete, workspace sightings and candidate scores persist, identical identifying parameters are idempotently rejected after completion, and a controlled normalization failure restarts the same JobInstance without repeating the completed HTTP discovery step.

Greenhouse client and normalizer tests verify the official public `GET /v1/boards/{board_token}/jobs?content=true` contract, local keyword/location filtering, raw JSON hashing, and provider-specific normalization. No live Greenhouse board is claimed.

## Automatic Greenhouse Board Enrichment Evidence

Flyway V13 adds the shared `discovered_source_board` registry and workspace-scoped `workspace_source_board` visibility. Setup no longer asks a user to choose sources or enter a Greenhouse board token. A direct, HTTPS Greenhouse-hosted URL from an existing public source is parsed only for documented `job-boards.greenhouse.io/{board}` or legacy `boards.greenhouse.io/{board}` forms. The application does not follow arbitrary tracking redirects.

During `jobDiscoveryStep`, a detected board is registered idempotently, linked to the workspace's search definition, and projected into a one-page Greenhouse `search_profile`. Its generated profile ID sorts after the workspace's Adzuna profile, so the existing restartable tasklet fetches it in the same six-step Find-jobs execution. Successful public API access marks the board `VALIDATED`; a failed source fetch is persisted as `FAILED` with a reason. `GET /api/source-boards` provides workspace-scoped REST/Swagger inspection.

Focused PostgreSQL Testcontainers plus MockWebServer verification ran on 2026-08-31. One direct Greenhouse URL in a mocked Adzuna posting created an internal board record, fetched the Greenhouse board, stored two workspace sightings, normalized/scored both jobs, and marked `examplebank` `VALIDATED`. Existing restart coverage proved that a completed discovery step is not repeated after a controlled downstream normalization failure. The detector unit test covers current/legacy official URL forms and rejects tracking, lookalike, and malformed URLs.

Final verification on 2026-08-31: `./mvnw clean test` completed with 64 tests, 0 failures, 0 errors, and 0 skipped. `spotless:apply` and `git diff --check` completed without output. The rebuilt Compose application returned health `UP`; Flyway recorded V13 as successful; `/api/source-boards` returned `[]` for a new browser workspace; `/setup` rendered the token-free automatic-source explanation; and `/v3/api-docs` exposed the `Discovered source boards` tag and operation descriptions.

## Optional Jooble Source and Portal Link-out Evidence

Flyway V14 extends the existing source checks in `search_profile`, `source_fetch_run`, `raw_job_posting`, and `normalized_job` to permit `JOOBLE`. `JoobleJobSourceClient` uses Jooble's documented regional `POST /api/{apiKey}` search contract, persists each returned job as raw provider JSON with a SHA-256 payload hash, supplies page checkpoints to the existing restartable discovery tasklet, and retries only transient HTTP/network failures. `JoobleJobPostingNormalizer` deterministically normalizes title, company, location, snippet, employment type, update time, source link, and content hash.

Jooble is deliberately opt-in: an active workspace always projects Adzuna, and projects Jooble only when `JOOBLE_API_KEY` is present at confirmation time. This prevents an ordinary Find-jobs run from failing merely because the optional credential is absent. Adding the key requires an application restart and one save/confirm cycle to create the source profile. No live Jooble listing is claimed without a supplied regional key.

The dashboard also creates outbound LinkedIn and JobStreet Singapore search links from saved keywords and location. They open the public portals directly and neither scrape nor import portal data. Existing per-job **Open on source** handling also works for Jooble through the shared view-and-redirect endpoint.

Verification on 2026-08-31: focused MockWebServer tests covered Jooble request path/body, pagination, raw-hash preservation, missing-key failure, malformed response rejection, and normalization. PostgreSQL Testcontainers onboarding coverage confirmed that a configured key produces both `ADZUNA` and `JOOBLE` workspace source profiles. `./mvnw clean test` completed with 69 tests, 0 failures, 0 errors, and 0 skipped. `spotless:apply` and `git diff --check` completed without output. The rebuilt Compose app returned health `UP`, Flyway recorded version `14`, and `/setup` rendered the Adzuna/optional-Jooble explanation. No live Jooble request was made because no regional key was supplied for this verification.

## Portal Search Hub Evidence

The dashboard separates imported/scored discovery from outbound portal searches. A small
`PortalSearchLinkFactory` creates preference-filled official search URLs for LinkedIn, JobStreet
Singapore, SEEK Australia, and SEEK New Zealand. Thymeleaf renders the links in a dedicated **Search
more job portals** panel with an explicit warning that those results are not copied into JobLens or
scored. No portal credentials, scraping, result parsing, or provider impersonation were introduced.

`PortalSearchLinkFactoryTests` verifies provider order, region labels, LinkedIn query encoding, and
deterministic keyword-slug URLs for all three SEEK-family destinations. Final verification on
2026-08-31: `./mvnw clean test` completed with 70 tests, 0 failures, 0 errors, and 0 skipped;
`git diff --check` completed without output. The rebuilt Compose app returned health `UP`; a real
workspace dashboard render contained **Search LinkedIn — Singapore**, **Search JobStreet —
Singapore**, **Search SEEK — Australia**, and **Search SEEK — New Zealand**, with the expected
preference-derived SEEK URLs.

## Live Jooble Acceptance Evidence

The user added a Singapore regional API key only to ignored `.env`; verification checked presence and
length without printing the value. The Compose app was recreated, and the existing workspace's exact
preferences were resaved and confirmed through `/setup/preferences` and `/setup/confirm`. Profile
version 1 became `SUPERSEDED`, version 2 became `ACTIVE`, and active `ADZUNA` plus `JOOBLE` search
profiles were verified through SQL.

`POST /api/batch/find-jobs/run?businessDate=2026-08-31` completed as JobExecution 22 / JobInstance 19.
Adzuna and Jooble each completed three pages and reported 60 received records. Jooble produced 60 raw
JSONB landing rows, 60 normalized rows with source URLs, 60 workspace sightings, and 60 candidate
scores ranging from 14 to 50. Adzuna's 60 received records reconciled to 58 unique raw identities.

All six Find Jobs steps completed with zero rollbacks: discovery, normalization (118/118), skill
extraction (60/60), exact duplicates, fuzzy suggestions, and scoring (120/120). REST inspection of job
442 returned source `JOOBLE`; the dashboard rendered **Open on JOOBLE**; and the workspace view
endpoint returned `302` with an original `https://sg.jooble.org/desc/...` location. The key was never
logged, queried from the container, written to tracked files, or included in command output.

## Smart Portal Query Planner Evidence

`PortalSearchQueryPlanner` now derives three deterministic intentions from the confirmed workspace
profile. The primary search combines the first preferred role with up to two target sectors. The
second combines the alternate role, up to two technologies found in both the saved keywords and
confirmed resume skills, and the primary sector. Compound skills win over their contained aliases, so
`Spring Boot` suppresses the redundant `Spring`. The third query keeps the saved keywords as a broad
fallback.

`PortalSearchLinkFactory` translates each intention to provider-appropriate syntax. LinkedIn receives
quoted Boolean expressions using supported `AND`, `OR`, and parentheses plus the saved location.
JobStreet Singapore, SEEK Australia, and SEEK New Zealand receive concise keyword slugs. The dashboard
shows portal/region, intent, generated query, and action for all twelve links, keeping the algorithm
visible rather than hiding it inside a URL.

Focused tests verify the exact role/sector/technology plan, compound-skill selection, fallback,
LinkedIn encoding, provider grouping, and regional SEEK URLs. Final verification on 2026-08-31:
`./mvnw clean test` completed with 71 tests, 0 failures, 0 errors, and 0 skipped; `spotless:apply` and
`git diff --check` completed without output. The rebuilt Compose app returned health `UP`. The real
Singapore workspace dashboard rendered 12 **Open search** actions, four occurrences of each of the
three intent labels, and the expected visible primary LinkedIn query: `"Senior Java Developer" AND
("banking" OR "payments")`.

Final verification on 2026-08-30:

```text
./mvnw clean test
BUILD SUCCESS
Tests run: 62, Failures: 0, Errors: 0, Skipped: 0
Flyway migrations applied by integration tests: 12
```

## Dockerized Application Milestone

`Dockerfile` uses a multi-stage Java 21 build and runs the packaged jar as an unprivileged user. Compose starts the application after PostgreSQL becomes healthy and passes database configuration through environment variables. Verify with `docker compose build app && docker compose up -d` and `curl http://localhost:8080/actuator/health`.

Live Adzuna verification completed after credentials were supplied through the ignored `.env`: Docker Compose app health returned `UP`; JobExecution 17 / JobInstance 14 for `jobDiscoveryJob` completed successfully on 2026-08-30.

## Weekly Market Insights and OpenAPI Milestone

Flyway V9 adds `weekly_market_insight`, keyed by week and source. `weeklyMarketInsightJob` aggregates normalized postings into weekly job, company, remote-job, and average-salary metrics with an idempotent upsert. Launch it with `POST /api/market-insights/run?weekStart=YYYY-MM-DD` and inspect results with `GET /api/market-insights?from=YYYY-MM-DD&to=YYYY-MM-DD`.

Swagger/OpenAPI is available through springdoc at `/swagger-ui.html` and `/v3/api-docs`. The API metadata identifies JobLens and documents the REST surface generated from the controllers.

## Application Lifecycle and Follow-up Generation Milestone

Flyway migration `V8__create_application_lifecycle.sql` adds `job_application`, immutable `application_status_history`, and `application_follow_up`. PostgreSQL constraints enforce one application per candidate/job, supported lifecycle and follow-up states, ordered completion fields, and one generated follow-up per application/status-history/type/version identity.

Allowed transitions are explicit and forward-only: `SAVED → APPLIED → SCREENING → INTERVIEW → OFFER → ACCEPTED`, with supported active stages able to exit to `REJECTED` or `WITHDRAWN`. Terminal states cannot transition. Each successful command locks the application row, validates the effective date, updates the current projection, and appends an immutable history event in one transaction.

`applicationFollowUpJob` contains `applicationFollowUpGenerationStep`. Its identifying parameters are `businessDate` and `followUpVersion=follow-up-v1`; `failAfterApplications` is a non-identifying controlled-failure parameter. The tasklet locks eligible application rows and deterministically generates application check-in (+7 days), recruiter check-in (+5), interview thank-you (+1), or offer-decision (+3) actions. A later transition cancels obsolete open actions, while completed actions remain historical. PostgreSQL upsert preserves unchanged timestamps.

REST commands and inspection are available at `POST/GET /api/applications`, `POST /api/applications/{id}/transitions`, `GET /api/follow-ups`, `POST /api/follow-ups/{id}/complete`, and `POST /api/batch/follow-ups/run`. Complex lifecycle SQL is externalized under `src/main/resources/sql/lifecycle*` and uses `NamedParameterJdbcTemplate`. The shared SQL resource loader moved from the intelligence package into neutral `com.ankit.joblens.jdbc` support.

Three policy unit tests and three PostgreSQL Testcontainers integration tests verify allowed/forbidden transitions, deterministic action mapping, immutable history, applied dates, duplicate protection, stale-action cancellation, completion, no-op timestamp idempotency, transactional failure rollback, and `JobOperator.restart` creating a new execution for the same JobInstance. Full result: `./mvnw clean test` completed with `BUILD SUCCESS`; 52 tests, 0 failures, 0 errors, 0 skipped.

Manual verification applied Flyway V8, created application 1 for `MANUAL-FUZZY-1`, and transitioned it from `SAVED` to `APPLIED` effective 2026-08-21. JobExecution 13 / JobInstance 12 completed with read/write 1/1, one commit, and zero rollbacks; REST and SQL showed `APPLICATION_CHECK_IN`, due 2026-08-28. JobExecution 14 / JobInstance 13 reran unchanged state with one row and identical creation/update timestamps.

Known limitation: follow-up rules are deterministic code configuration and the tasklet deliberately reconciles the personal-scale application set in one transaction. If volume grows, the same policy can move behind database-configured rules and chunked partitioning based on measured need.

## Explainable Fuzzy Duplicate Similarity Milestone

Flyway migration `V7__create_fuzzy_job_similarity.sql` adds `job_similarity`. It stores an ordered normalized-job pair, algorithm version, overall and per-dimension scores, explicit review decision, human-readable explanation, the two input content hashes, and an idempotent calculation timestamp. PostgreSQL checks constrain pair ordering, score ranges, hashes, decisions, and uniqueness per pair/algorithm.

`jobIntelligenceJob` now runs `fuzzyDuplicateDetectionStep` after exact clustering and before scoring. Exact-cluster pairs are excluded. Remaining pairs are blocked by significant title-token overlap, exact normalized company, or title trigram similarity before deterministic calculation. `fuzzy-v1` weights title 40, description 25, company 20, location 10, and employment 5; missing optional dimensions are removed from the effective denominator. The default stored threshold is 75 (`POSSIBLE_DUPLICATE`) and the `LIKELY_DUPLICATE` threshold is 90. Both are environment-configurable.

The stored result is an explainable review suggestion, not a probability, hidden-employer assertion, or automatic cluster merge. Reconciliation removes stale pairs, preserves `calculated_at` on a no-change run, and updates it only when inputs or calculated output change. The tasklet is transactional. Controlled `failFuzzyDetection=true` throws after reconciliation so tests prove rollback; `JobOperator.restart` then creates a new execution for the same JobInstance without replaying completed normalization, skill, or exact-detection steps.

REST inspection is available through:

* `GET /api/duplicates/similarities` with optional `decision` and `minimumScore`
* `GET /api/duplicates/similarities/{id}`
* the `similarityMatches` field on `GET /api/jobs/{id}`

Complex and reused duplicate SQL is now externalized below `src/main/resources/sql/duplicate` and `sql/duplicate-query`. Small repository classes load these statements and use `NamedParameterJdbcTemplate`, giving collection expansion and readable named parameters without introducing JPA. Existing simple positional and batch-oriented code can continue using `JdbcTemplate`; a repository-wide mechanical rewrite was deliberately avoided.

Automated verification added four calculator unit tests and three PostgreSQL Testcontainers integration scenarios. They cover candidate blocking, dimensional scoring, optional-field reweighting, ordered-pair validation, exact-pair exclusion, unrelated-row exclusion, REST fields and filters, stale-result cleanup, timestamp idempotency, transactional rollback, and same-JobInstance restart. Final command:

```bash
./mvnw clean test
```

Observed result: `BUILD SUCCESS`; 46 tests, 0 failures, 0 errors, 0 skipped. Flyway applied all seven migrations against PostgreSQL 17.11.

Manual verification used two controlled Adzuna-shaped raw rows. JobExecution 11 / JobInstance 10 completed all five intelligence steps. SQL and REST returned one `fuzzy-v1` pair, `MANUAL-FUZZY-1` to `MANUAL-FUZZY-2`, with overall score 84.19, title 78.60, description 71.00, company/location/employment 100.00, and `POSSIBLE_DUPLICATE`. The fuzzy step reported read 3/write 1/rollback 0. JobExecution 12 / JobInstance 11 reran unchanged input and retained the original `calculated_at`, proving no-op persistence idempotency.

Files introduced for this milestone include:

* `V7__create_fuzzy_job_similarity.sql`
* fuzzy similarity domain/calculator/tasklet and controlled-failure types
* duplicate detection and query repositories plus `ClasspathSql`
* duplicate SQL resources under `src/main/resources/sql/`
* `FuzzySimilarityCalculatorTests`

Known limitation: pair enumeration currently occurs in memory with cheap blocking. This is appropriate for the personal-scale dataset; database-side blocking should be introduced only when measured volume justifies it. Thresholds and weights are deterministic starting heuristics and should be calibrated against reviewed examples.

## Exact Duplicate Detection Milestone

Flyway migration `V6__create_exact_duplicate_detection.sql` adds `duplicate_cluster`, `duplicate_cluster_member`, and `duplicate_match_evidence`. Constraints enforce at least two members per persisted cluster, one canonical member, one cluster per normalized job, ordered evidence pairs, and the two allowed deterministic evidence types: `SOURCE_EXTERNAL_ID` and `NORMALIZED_CONTENT_HASH`.

`jobIntelligenceJob` now runs `exactDuplicateDetectionStep` after skill extraction and before scoring. The transactional tasklet loads normalized identities, computes connected components across exact source/external-ID and normalized-content-hash keys, chooses the lowest normalized-job ID as the stable canonical member, and reconciles clusters, memberships, and pair evidence. Obsolete clusters and evidence are removed when content changes. No-op reruns preserve creation, update, membership, and evidence timestamps.

The landing table already enforces unique `(source, external_job_id)`, so repeated provider sightings remain one raw/normalized identity rather than creating artificial duplicate rows. The exact detector still models that key as an edge and persists `SOURCE_EXTERNAL_ID` evidence if multiple normalized identities can contain it in a future schema. Under the current schema, distinct members normally carry `NORMALIZED_CONTENT_HASH` evidence.

REST inspection is available through `GET /api/duplicates`, `GET /api/duplicates/{id}`, and the `duplicateCluster` field on `GET /api/jobs/{id}`. Detail responses contain canonical/member fields and exact pair evidence. Fuzzy scores or implied employer relationships are not produced.

PostgreSQL Testcontainers verification expanded `JobIntelligenceIntegrationTests` from 7 to 10 tests. The three new scenarios verify exact hash grouping, one canonical member, persisted evidence, REST reads, exclusion of unique rows, no-op timestamp idempotency, changed-content cluster cleanup, transactional failure rollback, and `JobOperator` restart using the same JobInstance while completed normalization/skill steps are not replayed. Targeted result: 10 tests, 0 failures, 0 errors, 0 skipped.

Manual local verification applied Flyway version 6 and ran JobExecution 9 / JobInstance 8 to `COMPLETED`. Two controlled raw postings normalized to hash `ec0dc8cc0d271b255c5dcce9c7d27234a8ef152178f7ff36d065d90987b1729f`; SQL showed cluster `exact:v1:3`, canonical job 3, two memberships, and one `NORMALIZED_CONTENT_HASH` evidence row. Batch metadata showed duplicate-step read/write 3/2, one commit, and zero rollbacks. `GET /api/duplicates` and `GET /api/duplicates/1` returned the same cluster, members, and evidence. A no-change JobExecution 10 completed with one cluster/two memberships and unchanged cluster/evidence timestamps. SQL also found zero duplicate `(source, external_job_id)` groups.

Final verification command:

```bash
./mvnw clean test
```

Observed result: `BUILD SUCCESS`; 39 tests, 0 failures, 0 errors, 0 skipped. `git diff --check` also completed with no output.

Files introduced for this milestone:

* `V6__create_exact_duplicate_detection.sql`
* `ExactDuplicateDetectionTasklet`
* `InjectedDuplicateDetectionFailureException`
* `DuplicateQueryController`

Files extended for this milestone:

* `JobIntelligenceConfiguration`
* `JobIntelligenceController`
* `JobQueryController`
* `JobIntelligenceIntegrationTests`
* `AGENTS.md`, `README.md`, `SESSION_HANDOFF.md`, and `BUILD_PROGRESS.md`

## Skills, Candidate Profile, and Scoring Milestone

Flyway migration `V5__create_skills_candidate_scoring.sql` adds the canonical `skill` catalog, database-backed `skill_alias`, `job_skill`, seeded `candidate_profile`/`candidate_skill`/`candidate_preference`, and explainable `job_score`/`job_score_reason` tables. `normalized_job` now records skill and score content hashes for derived-data freshness.

`jobIntelligenceJob` now runs three chunk-oriented steps: `jobNormalizationStep`, `skillExtractionStep`, and `scoringStep`. The extraction reader selects normalized rows whose `skill_extraction_hash` differs from `normalized_content_hash`; scoring recalculates the current default candidate score and upserts one score per job/profile. Derived writes delete/reinsert job skills and score reasons inside their chunk transactions, so reruns are idempotent and changed normalized content refreshes derived data.

The extractor loads canonical names and aliases from PostgreSQL and applies case-insensitive, token-boundary-aware matching over title and cleaned description. Duplicate mentions resolve to one `job_skill` row with a mention count. The seeded catalog contains 30 canonical skills and 9 aliases, including SpringBoot, spring-boot, K8s, AWS, WebSphere MQ, J2EE, and Postgres mappings.

The default candidate profile is a configurable senior Java/backend engineer targeting Singapore and senior/backend/payments/integration roles. Twenty candidate skills are seeded; Java 21 is marked `LEARNING`. Preferences store the seven category weights (40/15/10/10/10/10/5), employment/work arrangement priorities, salary-missing treatment, and freshness bands.

Scoring is deterministic and bounded to 0..100. It combines candidate-skill overlap, explicit domain terms, seniority terms, Singapore/remote preference, employment type, salary availability, and posted-date freshness. One reason row is emitted per scored category and a database check constraint enforces category totals equal `job_score.total_score`; integration tests also assert the reason sum invariant.

Read APIs are available at `GET /api/jobs` and `GET /api/jobs/{id}`, returning normalized fields, extracted canonical skills, score categories, and score reasons. The intelligence launch remains `POST /api/batch/intelligence/run?businessDate=...` and uses `JobOperator`.

Verified commands:

```bash
./mvnw -q -DskipTests compile
set -a; source .env; set +a; ./mvnw -q -Dtest=JobIntelligenceIntegrationTests test
set -a; source .env; set +a; ./mvnw test
docker compose ps
curl -X POST 'http://localhost:8080/api/batch/intelligence/run?businessDate=2026-09-04'
docker compose exec -T postgres psql -U joblens -d joblens ...
```

The targeted PostgreSQL intelligence suite passes 7 tests. The full suite passes 36 tests with 0 failures, 0 errors, and 0 skipped. Local PostgreSQL migration V5 applied successfully; the HTTP launch returned JobExecution 8 / JobInstance 7 with `COMPLETED`. SQL showed 30 skills, 9 aliases, one default candidate profile, 20 candidate skills, 12 preferences, five extracted canonical skills for the manual Example Bank posting, and one score of 54 whose reason sum was also 54. Batch metadata for execution 8 showed normalization read 0/write 0, skill extraction read/write 1/1, and scoring read/write 1/1, with one commit per derived step and zero rollbacks.

The local API returned `GET /api/jobs/1` with score 54, technical 16, domain 0, seniority 10, location 8, employment 10, salary 5, freshness 5, and reasons reconciling exactly to 54. Skills were Java, Kafka, Spring, Spring Boot. Domain scoring correctly remained zero because the controlled description contained no explicit configured domain term.

Limitations for this checkpoint: scoring currently recalculates all normalized jobs each run (the score hash is persisted for observability); only one candidate profile is seeded; remote type is never inferred; fuzzy duplicates, lifecycle, dashboard, and market insights remain future milestones.

## Local Startup Configuration Fix

Observed a direct `./mvnw spring-boot:run` failure when the shell had not sourced `.env`: PostgreSQL requested SCRAM authentication but `spring.datasource.password` defaulted to empty. The existing Compose contract uses the safe local development password `joblens-local`, so the application default and `.env.example` are now aligned to that value while retaining `JOBLENS_DB_PASSWORD` overrides. Verified with environment variables explicitly unset: application startup completed, Flyway validated schema version 5, and `GET /actuator/health` returned `{"groups":["liveness","readiness"],"status":"UP"}`.

## Search Profile Import Milestone

### Schema and input

Flyway migration `V2__create_search_profile_import_tables.sql` created:

* `search_profile`, keyed by stable business ID `profile_id`, with source and employment-type checks
* `search_profile_rejection`, containing input file, row number, JSONB input, reason, execution ID, and timestamp

Runtime inputs:

* `data/import/search-profiles.csv` — four requested profiles
* `data/import/search-profiles-restart.csv` — six deterministic restart-demonstration profiles
* `src/test/resources/fixtures/search-profiles-invalid.csv` — one valid and five invalid profiles

### Batch design

* Job: `searchProfileImportJob`
* Step: `searchProfileImportStep`
* Chunk size: 2
* Reader: restartable `FlatFileItemReader`, header skipped, quoted CSV and blank fields supported
* Processor: validates business fields and deterministically normalizes casing, whitespace, and pipe-delimited skills
* Writer: JDBC batch `INSERT ... ON CONFLICT (profile_id) DO UPDATE`
* Skip policy: only `SearchProfileValidationException` and `FlatFileParseException`, limit 100
* Rejection listener: writes investigation data and human-readable reasons to PostgreSQL
* JDBC JobRepository explicitly enabled for Spring Batch 6; Boot 4.1 otherwise defaults to an in-memory repository
* Automatic job startup remains disabled

Identifying parameters are `inputFile` and `businessDate`. The development/test-only `failOnRow` parameter is non-identifying and disabled when omitted.

### APIs

* `POST /api/batch/search-profiles/import`
* `GET /api/batch/executions`

Manual error verification returned HTTP 409 for an already-completed JobInstance and HTTP 400 for an unreadable input file. Stack traces are excluded from HTTP error bodies.

### Automated verification

Commands:

```bash
./mvnw clean compile
./mvnw test
./mvnw -Dtest=SearchProfileImportJobIntegrationTests test
./mvnw clean test
```

Final result: `BUILD SUCCESS`; 12 tests, 0 failures, 0 errors, 0 skipped.

Processor tests cover a valid profile, blank profile ID, blank keywords, invalid source, invalid employment type, and skill normalization/deduplication.

PostgreSQL 17 Testcontainers tests cover valid import, invalid rejection persistence, duplicate upsert, changed-value update, JDBC Batch metadata, completion, and failure/restart checkpoint behavior. H2 is not used.

### Manual PostgreSQL and HTTP evidence

Normal import, business date `2026-08-29`:

* JobExecution 1, JobInstance 1, `COMPLETED`
* read 4, write 4, skip 0, commit 3, rollback 0

Invalid fixture, business date `2026-08-30`:

* JobExecution 2, JobInstance 2, `COMPLETED`
* read 6, write 1, skip 5, commit 4, rollback 5
* five rejection rows persisted for missing ID, unsupported source, missing keywords, unsupported employment type, and invalid boolean

Controlled restart, business date `2026-08-31`:

* Execution 3, Instance 3: `FAILED`; read 4, write 2, commit 1, rollback 2
* PostgreSQL contained the two rows from the committed first chunk
* Execution 4, Instance 3: `COMPLETED`; read 4, write 4, commit 3, rollback 0
* reader ExecutionContext after failure stored `searchProfileCsvReader.read.count=2`
* all six `SR` profiles exist exactly once; the restart did not reread/write the committed first chunk

The shared JobInstance proves that changing/removing the non-identifying failure parameter did not create a different business instance. The new JobExecution and persisted reader checkpoint prove this was a Spring Batch restart rather than application-level replay logic.

### Files introduced for this milestone

Main packages:

* `com.ankit.joblens.searchprofile` — CSV/domain records, processor, exceptions, JDBC writer, rejection listener, and job configuration
* `com.ankit.joblens.batchapi` — import/history controllers and response/error models

Tests:

* `SearchProfileProcessorTests`
* `SearchProfileImportJobIntegrationTests`

### Known limitations

CSV supports one physical line per record; multiline quoted fields are not required for the current search-profile format. The launch, history, and chunk-builder APIs were subsequently migrated to their Spring Batch 6 replacements.

## Sequential Adzuna Discovery Milestone

### Schema and configuration

Flyway migration `V3__create_job_discovery_raw_landing.sql` created:

* `source_fetch_run`, with profile/run status, page and record counts, next-page checkpoint, failure reason, and Batch instance/execution references
* `raw_job_posting`, with source/external-ID uniqueness, JSONB source payload, SHA-256 payload hash, first/last-seen timestamps, processing status, and Batch/fetch-run references

Flyway validated and applied all three migrations against PostgreSQL 17.11. The application started successfully and Actuator returned `UP`.

Adzuna settings are externalized as `ADZUNA_APP_ID`, `ADZUNA_APP_KEY`, base URL, timeout, max pages, page size, retry attempts, and retry backoff. Startup succeeds without credentials; a discovery attempt fails observably at execution time.

### Client and Batch design

* Source interface: `JobSourceClient.supports(JobSource)` and `search(SearchProfile, PageRequest)`
* Provider: `AdzunaJobSourceClient`, using WebClient and Adzuna's `/jobs/{country}/search/{page}` request model
* Job: `jobDiscoveryJob`
* Step: `jobDiscoveryStep`
* Processing: one HTTP page per repeat iteration, sequentially across active ADZUNA profiles
* Identifying parameters: `businessDate` and optional `profileId`; no random uniqueness parameter
* HTTP is executed with the step transaction suspended. Fetch-run creation, each page write/checkpoint, completion, and failure use short `REQUIRES_NEW` PostgreSQL transactions.
* Step ExecutionContext keys: `discovery.currentProfileId`, `discovery.lastCompletedProfileId`, `discovery.fetchRunId`, and `discovery.nextPage`
* PostgreSQL `source_fetch_run.next_page` provides a durable consistency fence in addition to the Spring Batch checkpoint.

Pagination stops on an empty page, an API-derived no-more-results condition, or the configured maximum. HTTP 429, 500, 503, request timeouts, and connection failures are retried with bounded backoff. Other 4xx responses and malformed JSON are not retried.

Raw individual job JSON is retained directly from each result node. JDBC batch upsert uses `(source, external_job_id)` as the idempotency key. Unchanged payloads retain raw JSON, hash, `first_seen_at`, and `updated_at` while advancing `last_seen_at`. Changed payloads replace JSON/hash and advance `updated_at`, while retaining `first_seen_at`.

### Automated verification

Commands:

```bash
./mvnw -Dtest=AdzunaJobSourceClientTests test
./mvnw -Dtest=JobDiscoveryIntegrationTests test
set -a; source .env; set +a; ./mvnw clean test
git diff --check
```

Final result: `BUILD SUCCESS`; 25 tests, 0 failures, 0 errors, 0 skipped. Discovery contributed 7 MockWebServer client tests and 6 PostgreSQL Testcontainers integration tests.

Verified cases include official request construction, raw JSON retention, one/multiple/empty-page discovery, inactive-profile exclusion, uniqueness, unchanged and changed payload behavior, first-seen preservation, JSONB access, transient status retry for 429/500/503, non-retryable 401, malformed JSON, missing credentials, completed/failed fetch runs, and failed/restarted Batch metadata.

### Restart evidence

The deterministic PostgreSQL Testcontainers scenario fetched and committed pages 1 and 2, then exhausted all three attempts for page 3:

* JobInstance 2, JobExecution 2: `FAILED`
* committed raw rows: 4
* fetch state: pages 2, records 4, `next_page=3`
* Step ExecutionContext: `discovery.nextPage=3`

Restarting the same identifying parameters produced JobExecution 3 for the same JobInstance 2. The first restarted HTTP request was page 3 (pages 1 and 2 were not replayed), status became `COMPLETED`, fetch totals became 3 pages/5 records, and all five external IDs existed exactly once.

### Manual PostgreSQL and HTTP evidence

The application started on port 8080 against the Docker PostgreSQL container, Flyway reported schema version 3 current, and `GET /actuator/health` returned `UP`.

Because `ADZUNA_APP_ID` and `ADZUNA_APP_KEY` are absent locally, the manual call for SP001 intentionally demonstrated the real missing-credential path:

* JobInstance 4, JobExecution 5: `FAILED`
* `source_fetch_run`: source ADZUNA, profile SP001, status FAILED, pages 0, records 0, next page 1
* failure reason identifies `MissingJobSourceCredentialsException`
* `raw_job_posting`: 0 rows, so failure was not interpreted as an empty successful fetch
* `jobDiscoveryStep`: FAILED, commit 0, rollback 1

No live Adzuna success is claimed. A single small Singapore live verification remains pending until credentials are supplied through the documented environment variables.

### Files introduced for this milestone

Main packages:

* `com.ankit.joblens.discovery` — source contract, Adzuna WebClient, raw records, persistence service, restartable tasklet, and job configuration
* `com.ankit.joblens.batchapi` — discovery launcher and shared launch-response mapping

Tests:

* `AdzunaJobSourceClientTests`
* `JobDiscoveryIntegrationTests`

### Known limitations

Discovery is deliberately sequential; partitioning is deferred until the sequential model has production usage evidence. Adzuna credentials were not present, so only mocked provider behavior and the live missing-credential failure path were verified. The optional native Netty macOS DNS resolver is not installed; tests using localhost passed with the JDK/system resolver fallback.

## Spring Batch 6 API Modernization

Application and test code no longer injects or invokes the deprecated `JobLauncher` or `JobExplorer` APIs. Batch launch controllers and integration tests use `JobOperator.start(Job, JobParameters)`. The discovery restart test uses `JobOperator.restart(JobExecution)`, preserving the same JobInstance and checkpoint behavior. Search-profile failure injection still starts the failed instance with the same identifying parameters and without its non-identifying one-shot failure parameter, which is required to disable that controlled failure on the next execution.

Execution-history lookup now uses `JobRepository`, which owns the lookup operations in Spring Batch 6. Spring's `@EnableBatchProcessing` infrastructure supplies the modern `TaskExecutorJobOperator`; no application bean for deprecated `TaskExecutorJobLauncher` or `SimpleJobOperator` was introduced.

The search-profile step now uses the Spring Batch 6 `chunk(int)` builder and configures its transaction manager explicitly, replacing the deprecated `chunk(int, PlatformTransactionManager)` overload.

Repository scan after the change found no application or test references to `JobLauncher`, `JobExplorer`, `TaskExecutorJobLauncher`, or `SimpleJobOperator`. `./mvnw clean test` completed with `BUILD SUCCESS`: 25 tests, 0 failures, 0 errors, 0 skipped. Main and test compilation emitted no Spring Batch deprecation warnings.

## Raw-to-Normalized Intelligence Milestone

### Schema and state lifecycle

Flyway migration `V4__create_normalized_job.sql`:

* migrates legacy raw status `PENDING` to `NEW`
* defines `NEW`, `NORMALIZED`, `REJECTED`, and `FAILED`
* adds `processing_reason` and `processed_at` to `raw_job_posting`
* creates `normalized_job` with one-to-one raw FK/uniqueness, exact numeric salaries, timezone-aware posted time, canonical hash constraints, and focused source/external-ID and posted-time indexes

State meanings:

* `NEW`: eligible for normalization
* `NORMALIZED`: normalized row and raw state committed together
* `REJECTED`: structurally/business-invalid input with a human-readable reason
* `FAILED`: unexpected processing failure, eligible for retry/restart

Discovery preserves an existing status for unchanged payloads. When its raw payload hash changes, it atomically replaces the raw JSON/hash and resets status to `NEW`, clearing prior processing state. Tests verify unchanged payloads remain `NORMALIZED` and changed payloads become `NEW`.

### Batch and normalization design

* Job: `jobIntelligenceJob`
* Step: `jobNormalizationStep`
* Default chunk size: 20 (`JOBLENS_INTELLIGENCE_CHUNK_SIZE`); integration proof uses 2
* Identifying parameters: `businessDate` and `normalizationVersion=v1`
* Development/test-only `failAfterItems` is non-identifying
* Launch API: `POST /api/batch/intelligence/run`

The restartable keyset `RawJobPostingReader` reads `NEW`/`FAILED` records in ascending raw ID order. It stores `rawJobPostingReader.lastCommittedId` in the Step ExecutionContext. This avoids an unsafe cursor-offset restart when committed rows leave the eligible set after becoming `NORMALIZED`.

`AdzunaJobPostingNormalizer` owns all Adzuna JSON paths: title, `company.display_name`, `location.display_name`, description, contract fields, salaries/currency, explicit remote type, `created`, and `redirect_url`. Optional absent or malformed optional values become null; title and object-shaped raw JSON are required.

Jsoup 1.23.2 parses descriptions, decodes entities, preserves element word boundaries, and collapses excessive whitespace. Manual output verified `Senior Java Engineer & API owner Spring Boot Kafka` without cross-tag concatenation.

The normalized SHA-256 uses unambiguous length-prefixed canonical values for title, company, location, cleaned description, employment type, salary range/currency, and remote type. It excludes IDs, timestamps, URLs, raw JSON order, and processing metadata. Unit tests verify determinism, JSON property-order independence, semantically equivalent HTML, and volatile metadata independence.

`NormalizedJobWriter` performs the normalized upsert and raw transition to `NORMALIZED` within the same Batch chunk transaction. Raw uniqueness prevents duplicate normalized rows. Reprocessing updates an existing normalized row, preserves `created_at`, and changes `updated_at` only when the normalized content hash changes.

### Automated verification

Commands included:

```bash
./mvnw clean compile
./mvnw -Dtest=AdzunaJobPostingNormalizerTests test
./mvnw -Dtest=JobIntelligenceIntegrationTests test
set -a; source .env; set +a; ./mvnw clean test
git diff --check
```

Final result: `BUILD SUCCESS`; 35 tests, 0 failures, 0 errors, 0 skipped. The milestone adds four focused normalizer/hash/HTML tests and six PostgreSQL Testcontainers job tests while retaining all import and discovery coverage.

Covered behavior includes complete field extraction, HTML/entity handling, optional fields, salaries, missing title/non-object rejection, normalized persistence, raw status/reasons, idempotent reruns, changed-payload reprocessing, unchanged-payload exclusion, deterministic hashes, chunk counts, failed execution, `JobOperator.restart(JobExecution)`, Batch metadata, and checkpoint resumption.

### Restart evidence

The PostgreSQL integration scenario uses chunk size 2 and five ordered raw rows. Execution 9 on JobInstance 9 committed the first two normalized rows, then injected an unexpected failure on the third row:

* failed status: `FAILED`
* normalized rows after failure: 2
* third raw row status: `FAILED`
* saved checkpoint: the second raw ID

`JobOperator.restart(failedExecution)` created execution 10 for the same JobInstance 9. Failure injection is deliberately active only on the first execution. The restarted step read exactly the remaining three rows, completed all five rows without duplicate `raw_job_posting_id` values, and finished `COMPLETED`.

### Manual HTTP and SQL evidence

The Docker PostgreSQL database migrated successfully to Flyway version 4. Two controlled fixtures were inserted: one realistic valid Adzuna row and one missing-title row. The application started on port 8080 and this request completed:

```text
POST /api/batch/intelligence/run?businessDate=2026-09-03
JobInstance 6, JobExecution 7, COMPLETED
```

Batch step evidence:

```text
read=2, write=1, process_skip=1, commit=1, rollback=0
```

Raw evidence:

```text
MANUAL-NORM-1   NORMALIZED
MANUAL-NORM-BAD REJECTED  Adzuna job title is required
```

Normalized evidence for `MANUAL-NORM-1`:

```text
title: Senior Java Engineer
company/location: Example Bank / Singapore
description: Senior Java Engineer & API owner Spring Boot Kafka
employment: PERMANENT
salary: SGD 90000.00-120000.00
remote: HYBRID
posted: 2026-08-28T09:30:00Z
hash: 4c4fb023eb4876f96d9adaaf31f7813612401c58306ab835f7c991bf7d47f501
```

### Known limitations

Only Adzuna normalization exists, by design. Remote type is populated only from an explicit supported provider value; JobLens does not infer it from free text. Invalid optional salary/timestamp values are retained in raw JSON but normalized to null. Skills, duplicates, scoring, lifecycle, and market analysis are outside this milestone.
