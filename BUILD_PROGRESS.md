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
| 8  | One real internet job source works               | NOT STARTED |
| 9  | Raw postings stored idempotently                 | NOT STARTED |
| 10 | API pagination and restart work                  | NOT STARTED |
| 11 | Normalization works                              | NOT STARTED |
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

The CSV search-profile import and restartability milestone is complete.

## Next Observable Milestone

Implement Adzuna discovery as the next isolated milestone. Do not add normalization, scoring, or lifecycle work until sequential raw discovery is verified.

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

Spring Batch 6.0.5 marks the legacy `JobLauncher`, `JobExplorer`, and legacy chunk-builder path for future removal. They are functional and verified in this milestone, but should be migrated to the Batch 6 replacement operator/chunk APIs before a future Batch 7 upgrade. CSV supports one physical line per record; multiline quoted fields are not required for the current search-profile format.
