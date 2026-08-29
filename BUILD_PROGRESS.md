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
| 8  | One real internet job source works               | IN PROGRESS |
| 9  | Raw postings stored idempotently                 | COMPLETE    |
| 10 | API pagination and restart work                  | COMPLETE    |
| 11 | Normalization works                              | COMPLETE    |
| 12 | Skill extraction works                           | NOT STARTED |
| 13 | Duplicate detection works                        | NOT STARTED |
| 14 | Candidate scoring works                          | NOT STARTED |
| 15 | Lifecycle and follow-up generation work          | NOT STARTED |
| 16 | Weekly market insight works                      | NOT STARTED |
| 17 | Dashboard works                                  | NOT STARTED |
| 18 | Integration tests pass                           | NOT STARTED |
| 19 | Dockerized application works                     | NOT STARTED |
| 20 | README and interview demonstration complete      | NOT STARTED |

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

The first raw-to-normalized `jobIntelligenceJob` slice is complete and verified against PostgreSQL. It intentionally stops before skill extraction, duplicate analysis, scoring, and lifecycle work.

## Next Observable Milestone

Add deterministic database-driven skill extraction and aliases as the next isolated milestone. Do not add scoring or lifecycle work before skill extraction is verified.

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
