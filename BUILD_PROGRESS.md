# JobLens Build Progress

## Project

**JobLens**

Production-style personal job-market intelligence platform built around Spring Batch.

## Current Environment

| Item           | Status      | Evidence                                                            |
| -------------- | ----------- | ------------------------------------------------------------------- |
| macOS          | COMPLETE    | Development machine verified                                        |
| Apple Silicon  | COMPLETE    | Architecture reported as `aarch64`                                  |
| Java 21        | COMPLETE    | Temurin 21.0.12.1 active                                            |
| Maven          | COMPLETE    | Maven 3.9.16 verified                                               |
| Maven Wrapper  | COMPLETE    | `mvnw`, `mvnw.cmd`, and `.mvn/` present                             |
| Git            | COMPLETE    | Repository initialized on `main`; baseline commit exists            |
| IntelliJ IDEA  | COMPLETE    | Repository opened and accessible to Codex                           |
| Docker Desktop | IN PROGRESS | Correct Apple Silicon installation is still pending/being completed |
| PostgreSQL     | NOT STARTED | Intended to run locally through Docker Compose                      |

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
| 1  | Environment verified                             | IN PROGRESS |
| 2  | Spring Initializr project generated manually     | COMPLETE    |
| 3  | Generated application compiles and starts        | IN PROGRESS |
| 4  | PostgreSQL starts through Docker Compose         | NOT STARTED |
| 5  | Flyway and Spring Batch metadata tables verified | NOT STARTED |
| 6  | CSV search-profile import job works              | NOT STARTED |
| 7  | Failed CSV import and restart demonstrated       | NOT STARTED |
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

Java 21 has been installed and activated.

Verified runtime:

`Temurin 21.0.12.1`

Maven also reports Java 21 as its runtime.

### Maven Compilation

The following command has already been executed manually:

```bash
./mvnw clean compile
```

Observed result:

`BUILD SUCCESS`

Compilation reported Java release 21.

Therefore the generated Java source successfully compiles with Java 21.

### Git Baseline

The repository has been initialized with Git on branch:

`main`

Initial baseline commit:

`d5c013c - Initialize JobLens project`

This commit represents the generated application plus `AGENTS.md` before application configuration work begins.

## Current Expected Failure

The following command has already been executed:

```bash
./mvnw test
```

The generated test:

`JoblensApplicationTests.contextLoads`

currently fails while Spring attempts to create the application context.

Observed root cause includes:

`Failed to determine a suitable driver class`

The application contains JDBC/database infrastructure but PostgreSQL has not yet been configured and started.

This is currently an expected infrastructure failure.

Do not make this test green by:

* adding H2
* removing JDBC
* removing PostgreSQL
* removing Flyway
* disabling datasource auto-configuration
* deleting or disabling the context test

The intended fix is to introduce the actual PostgreSQL infrastructure and datasource configuration.

## Current Repository State

Important existing files include:

```text
AGENTS.md
pom.xml
mvnw
mvnw.cmd
.mvn/
src/main/java/com/ankit/joblens/JoblensApplication.java
src/main/resources/application.properties
src/test/java/com/ankit/joblens/JoblensApplicationTests.java
```

Current `application.properties` contains only:

```properties
spring.application.name=joblens
```

No application Spring Batch jobs have been implemented.

No application database migrations have been implemented.

No external job-source integrations have been implemented.

## Current Milestone

Establish the local application infrastructure required before implementing the first Spring Batch job.

The upcoming work will include:

* disable automatic Spring Batch job launching
* externalize PostgreSQL datasource configuration
* protect local secrets
* create `.env.example`
* start PostgreSQL
* verify datasource connectivity
* verify Flyway
* verify Spring Batch metadata tables

## Next Observable Milestone

Start PostgreSQL and prove:

1. PostgreSQL is running.
2. JobLens can establish a datasource connection.
3. HikariCP initializes successfully.
4. Flyway initializes successfully.
5. Spring Batch metadata tables exist.
6. The application context can start against the intended infrastructure.

Do not begin the CSV import job until this infrastructure milestone is verified.
