# JobLens Agent Instructions

## Project Objective

JobLens is a production SaaS product for multi-user, explainable job-market intelligence. V1 is the
current release line and `1.0.0-SNAPSHOT` is its development artifact version. The
repository began as a Spring Batch learning build, but learning value is no longer the product
boundary or an acceptable reason to treat production capabilities as optional. Spring Batch remains
an implementation tool for durable, restartable ingestion and reprocessing.

The intended pipeline is:

CSV search profiles
→ public job APIs
→ raw landing storage
→ normalization
→ skill extraction
→ duplicate detection
→ candidate scoring
→ application lifecycle
→ market insights
→ React dashboard

This remains a guided incremental build. Do not attempt to implement the entire startup architecture
at once, and do not claim launch readiness until the gates in `PRODUCT_REQUIREMENTS.md` have evidence.
Always call the product **JobLens** and identify **V1** as its current release line when version
context matters. Describe release readiness separately by stating its verified and incomplete
capabilities precisely; do not turn delivery status into part of the product name.

## Current Technology Baseline

* Java 21
* Spring Boot 4.1.1
* Maven with Maven Wrapper
* Spring Batch
* Spring JDBC
* PostgreSQL
* Flyway
* Spring Web MVC
* Thymeleaf
* Spring Boot Actuator
* JUnit
* Git
* Docker / Docker Compose for local PostgreSQL and Testcontainers

The original design requested Spring Boot 3.x.

The generated project currently uses Spring Boot 4.1.1.

Treat this as a deliberate recorded deviation and validate compatibility incrementally rather than changing versions without instruction.

## Mandatory Working Method

Before changing code:

1. Read this file.
2. Read `ENGINEERING_STANDARDS.md` and `BUILD_PROGRESS.md` if they exist.
3. Identify the requirement/bug ID, acceptance criteria, and required review evidence.
4. Inspect the actual existing files related to the requested change.
5. Preserve existing user-created work.
6. Implement only the requested/current milestone.
7. Do not silently advance into future milestones.

After changes:

1. List files created or modified.
2. Explain what changed and why.
3. Run appropriate compilation/tests.
4. Report actual failures instead of hiding them.
5. Update `BUILD_PROGRESS.md` only when observable evidence supports progress.
6. Apply the review gates and Definition of Done in `ENGINEERING_STANDARDS.md`.

Do not claim something works unless verified through command output, tests, database queries, HTTP responses, or another observable result.

## Current Verified Repository State

The repository currently has these verified working slices:

* PostgreSQL 17 through Docker Compose, Flyway, and Spring Batch 6 metadata
* `searchProfileImportJob` with CSV validation, rejection persistence, PostgreSQL upsert, and restartability
* sequential `jobDiscoveryJob` with the Adzuna client, pagination, bounded retries, raw JSONB landing, payload hashing, and checkpoint restart
* `jobIntelligenceJob` with normalization, HTML cleaning, normalized content hashing, database-driven skill aliases, candidate profile configuration, deterministic scoring, score explanations, and idempotent derived writes
* exact duplicate clustering with deterministic canonical membership and persisted source/external-ID or normalized-content-hash evidence
* deterministic fuzzy duplicate suggestions with explainable dimension scores, threshold decisions, idempotent reconciliation, and restartability
* audited application lifecycle transitions and restartable, idempotent follow-up generation
* REST launch/history APIs and `GET /api/jobs` plus `GET /api/jobs/{id}`
* JobOperator-based Batch 6 launch/restart infrastructure and JobRepository history lookup
* weeklyMarketInsightJob with idempotent weekly aggregates and REST inspection
* Swagger/OpenAPI inspection at `/swagger-ui.html` and `/v3/api-docs`
* React dashboard (`frontend/`, Vite-bundled) served at `/`, `/dashboard`, `/setup`, and
  `/applications` — every one of those routes forwards to the same SPA; no controller returns a
  Thymeleaf view for a current user-facing page
* Dockerized application image with Compose PostgreSQL dependency
* anonymous browser workspaces with validated, versioned resume/profile onboarding and UI-managed preferences
* workspace-owned search definitions, job sightings, rankings, views, applications, and follow-ups
* `findJobsJob`, a six-step one-click workflow from discovery through candidate scoring
* source-adapter registry with Adzuna, optional multi-country Jooble (one API key per country;
  Singapore, Malaysia, and India configured as of 2026-09-11), and safe automatic enrichment through
  the public Greenhouse and Lever posting APIs
* normalized multi-market search targets with independent provider profiles, checkpoints, scoring evidence, and market-aware portal links
* non-blocking, versioned resume machine-readability guidance with review acknowledgement and a two-step setup flow with one transactional review/activation action
* inclusive categorized skill and role intelligence with explainable resume evidence, workspace-private additions, provider-aware country selection, and versioned ESCO taxonomy import
* an explicit system-design baseline covering requirements, operating parameters, API/Batch boundaries, failure handling, and measurable scale triggers
* outbound Portal Search Hub for LinkedIn, JobStreet Singapore, SEEK Australia, and SEEK New Zealand without scraping or importing portal results
* deterministic smart portal queries derived from preferred roles, sectors, confirmed resume skills, and a broad fallback
* workspace-scoped resume skill review with draft-before-activation semantics
* React application lifecycle and candidate-scoped follow-up controls
* universal role-aware scoring with versioned Frontend, Backend, AI/ML, and Sales/Customer Success overlays and per-role evidence
* a versioned `qualifiesRecommended` signal (`universal-v2`) gating the dashboard's genuine
  Recommended jobs from an "Explore other results" section, instead of always featuring the
  highest-scored job in a weak result set (S0.3, 2026-09-11)
* a bundled, country-scoped city-suggestion catalogue (GeoNames-derived, CC BY 4.0) for the setup
  page's search-market editor
* a test-gated GitHub Actions pipeline (`.github/workflows/deploy.yml`) that runs the full Java and
  frontend suites on every push to `main` and only then deploys via the Render API; Render's own
  auto-deploy trigger is deliberately left off to avoid a second, untested deploy path

The current anonymous flow is a verified JobLens V1 development baseline, not a launch-ready service. Identity,
authorization, shared/provider-budgeted ingestion, real-time run control, secure résumé storage,
privacy workflows, observability, HA/backups, and delivery infrastructure remain launch gaps. Do not
silently implement all gaps together; select and verify one approved milestone at a time. Unsupported
portal scraping remains prohibited, and microservices still require an explicit measured trigger.

Read `PRODUCT_REQUIREMENTS.md` for the product/launch contract, `SYSTEM_DESIGN.md` for the startup
architecture, `SESSION_HANDOFF.md` for the resume point, `NEXT_MILESTONES.md` for selectable work,
`ENGINEERING_STANDARDS.md` for mandatory development/review gates, `README.md` for the local operator runbook, and `BUILD_PROGRESS.md` for historical evidence before
beginning a new session.

## Build Commands

Prefer Maven Wrapper:

```bash
./mvnw clean compile
./mvnw test
git diff --check
```

Do not depend on globally installed Maven when the wrapper is available.

PostgreSQL/Testcontainers tests require Docker Desktop to be running. For local application startup, `docker compose up -d` followed by `./mvnw spring-boot:run` is sufficient; the application has safe local database defaults matching `.env.example`, while real credentials must still be supplied through environment variables.

## Architecture Rules

JobLens starts as a modular monolith that can run the same versioned codebase in independently scaled
API and worker process roles. User count alone does not justify service extraction.

Do not introduce:

* microservices
* Kafka merely for architectural complexity
* distributed messaging without a real requirement
* an LLM or external AI service before deterministic processing works
* Lombok
* unnecessary abstractions
* empty package hierarchies with no implementation

Prefer simple architecture that can evolve when measured scaling, fault-isolation, deployment, data
ownership, or team-ownership pressure requires abstraction. Production requirements are not
permission to add infrastructure without an approved milestone and evidence.

Use Java records where they improve immutable data-transfer structures.

## Spring Batch Rules

Automatic execution of every Spring Batch job on application startup must remain disabled.

Jobs should eventually be launched explicitly with meaningful identifying parameters.

For every batch implementation consider:

* JobInstance vs JobExecution
* StepExecution
* chunk boundaries
* transaction boundaries
* checkpointing
* ExecutionContext
* restartability
* retries
* skips
* listeners
* idempotency
* failure halfway through processing

Skipped records must have observable rejection or failure reasons.

Do not silently swallow records.

Do not use broad `catch (Exception)` blocks to hide failures.

External HTTP/API calls must not execute inside an uncontrolled database transaction.

## Database Rules

PostgreSQL is the application database.

Use Flyway for application schema migrations.

Create the schema incrementally.

Do not place the entire future database model in the first migration.

Do not replace PostgreSQL integration behaviour with H2 when database semantics matter.

For batch-oriented writes, prefer Spring JDBC and `JdbcBatchItemWriter` where appropriate.

Do not introduce JPA merely because it is convenient.

Spring Batch metadata tables must eventually use the real PostgreSQL datasource.

## Security and Configuration

Never commit:

* passwords
* API keys
* access tokens
* credentials
* personal secrets

Use environment variables for runtime secrets.

`.env` must remain ignored by Git.

`.env.example` may document variable names and safe example values.

Do not assume Spring Boot automatically loads `.env`.

Never log credentials or API keys.

## Job Source Rules

Only legitimate public APIs and public job-board interfaces should be used.

Do not scrape LinkedIn.

Do not scrape Indeed.

The first general job-source implementation should be verified against the provider's current API before implementation rather than relying on assumptions.

Raw API responses must eventually be persisted before business normalization.

## Processing Rules

Initial skill extraction, scoring and duplicate detection must be deterministic and explainable.

Do not introduce an LLM for the first implementation.

Duplicate detection will eventually support:

1. source + external ID
2. normalized content hash
3. explainable fuzzy similarity

Similarity must be reported as evidence or probability, not as unsupported claims about hidden employers or end clients.

## Testing Rules

Testing is part of each milestone.

Eventually use:

* JUnit
* Spring Batch Test
* Testcontainers with PostgreSQL
* mock HTTP server for external APIs
* processor unit tests
* step tests
* job integration tests

Do not make a failing test green merely by disabling the real infrastructure that the application requires.

## Git Safety

Never use destructive Git commands.

Do not:

* reset user work
* force checkout files
* delete uncommitted changes
* force push
* rewrite history

Always preserve work already present in the repository.

## Current Scope Rule

Only implement the milestone explicitly requested by the user.

If the current task is PostgreSQL configuration, do not start CSV processing.

If the current task is CSV import, do not start internet job discovery.

If the current task is discovery, do not start scoring.

Stop at meaningful checkpoints.

## Progress Tracking

`BUILD_PROGRESS.md` is the authoritative record of:

* completed milestones
* current milestone
* expected failures
* verified evidence
* next milestone

Read it before significant changes.

Update it after verified progress.
