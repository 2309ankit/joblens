# JobLens Agent Instructions

## Project Objective

JobLens is a production-style personal job-market intelligence application built primarily to learn and demonstrate Spring Batch in a realistic system.

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
→ Thymeleaf dashboard

This is a guided incremental build. Do not attempt to implement the entire architecture at once.

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
* Docker / Docker Compose planned for local PostgreSQL

The original design requested Spring Boot 3.x.

The generated project currently uses Spring Boot 4.1.1.

Treat this as a deliberate recorded deviation and validate compatibility incrementally rather than changing versions without instruction.

## Mandatory Working Method

Before changing code:

1. Read this file.
2. Read `BUILD_PROGRESS.md` if it exists.
3. Inspect the actual existing files related to the requested change.
4. Preserve existing user-created work.
5. Implement only the requested/current milestone.
6. Do not silently advance into future milestones.

After changes:

1. List files created or modified.
2. Explain what changed and why.
3. Run appropriate compilation/tests.
4. Report actual failures instead of hiding them.
5. Update `BUILD_PROGRESS.md` only when observable evidence supports progress.

Do not claim something works unless verified through command output, tests, database queries, HTTP responses, or another observable result.

## Build Commands

Prefer Maven Wrapper:

```bash
./mvnw clean compile
./mvnw test
git diff --check
```

Do not depend on globally installed Maven when the wrapper is available.

## Architecture Rules

JobLens starts as a modular monolith.

Do not introduce:

* microservices
* Kafka merely for architectural complexity
* distributed messaging without a real requirement
* an LLM or external AI service before deterministic processing works
* Lombok
* unnecessary abstractions
* empty package hierarchies with no implementation

Prefer simple architecture that can evolve when a second real use case requires abstraction.

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

After creating `AGENTS.md`:

1. Show the file path.
2. Confirm no other files were changed.
3. Run `git status --short`.
4. Do not create `BUILD_PROGRESS.md` yet.
5. Stop.
