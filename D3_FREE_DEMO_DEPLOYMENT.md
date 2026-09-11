# D3 Free Demo Deployment

Status: **SELECTED by the product owner on 2026-09-10**.

Requirement ID: `D3-DEMO-DEPLOY-01`

## Problem and outcome

JobLens has a verified local Docker artifact but no remotely reachable environment. The product owner
wants a zero-cost starting point that can be replaced by AWS infrastructure when usage justifies it.
This checkpoint deploys the existing modular monolith as a disposable demonstration environment; it
does not satisfy the S6 production-platform gate or authorize real-user data.

The actor is the product owner or an invited evaluator using synthetic or redacted résumé content.
The successful outcome is a public HTTPS Render URL in Singapore, backed by a Neon PostgreSQL 17
database in Singapore, that starts from the repository, applies Flyway migrations, reports healthy,
and serves the JobLens setup surface.

## Acceptance criteria

1. A clean repository checkout builds one immutable JobLens application image from the checked-in
   multi-stage `Dockerfile`; the runtime image contains Java 21 but no Maven, Node installation, source
   tree, or build-time credentials, and runs as a non-root user.
2. A checked-in Render Blueprint defines exactly one Singapore Free web service, deploys the Docker
   image with controlled manual promotion, uses `/actuator/health` for health checks, and contains no
   database or provider credentials.
3. Runtime configuration accepts a Neon PostgreSQL JDBC URL, username, and password exclusively
   through environment variables. TLS is required by the supplied Neon connection URL. The Render
   process uses a bounded JDBC pool suitable for the free database and a JVM memory policy suitable
   for a 512 MB container.
4. The externally reachable deployment returns HTTP 200 with `UP` from `/actuator/health`, serves
   `/setup`, loads its content-hashed React assets, and issues the anonymous development-workspace
   cookie with `Secure`, `HttpOnly`, and `SameSite=Lax` attributes.
5. A fresh Neon database applies every Flyway migration through V26 successfully. No manual schema
   edits or seed data are required.
6. The operator runbook documents initial deployment, safe secret entry, smoke verification,
   expected cold starts, free-tier limits, database-size inspection, rollback, and migration seams.
7. Maven tests, the frontend tests included in that lifecycle, formatting, `git diff --check`, a local
   memory-constrained container smoke test, and remote HTTP/database evidence pass before completion.

## Design and operating boundary

```text
Evaluator browser
      | HTTPS
      v
Render Free web service, Singapore
      | PostgreSQL JDBC with TLS
      v
Neon Free PostgreSQL 17, Singapore
```

- The Spring Boot process continues to own the React bundle, MVC/API surface, Flyway, and explicitly
  launched Spring Batch jobs. No frontend split, microservice, queue, or separate worker is added.
- Render builds the artifact from the repository. Build-time configuration is non-secret. Runtime
  database and optional provider values are entered in the Render dashboard and never committed.
- **Superseded 2026-09-11**: automatic deploys were originally disabled here because the repository
  had no CI/CD promotion gate — a deploy was an explicit operator action. The owner asked for
  auto-deploy on 2026-09-10; that was first enabled as Render's own unconditional GitHub-push
  auto-deploy, then replaced the next day with a real test gate: `.github/workflows/deploy.yml` now
  runs the full test suite on every push to `main` and only deploys via the Render API on success
  (Render's native auto-deploy trigger is off, so it can't race the test gate — see `README.md`'s
  **Deployment pipeline** section). This is still short of the full S6 production platform gate (no
  staging, no security gates, no progressive rollout).
- The database is external to Render so it does not inherit Render Free PostgreSQL's 30-day expiry.
  The application remains plain PostgreSQL/Flyway/JDBC and can later move through a PostgreSQL dump or
  replication path to Amazon RDS without application data-access redesign.
- The anonymous workspace is a development identity, not authentication. The environment is limited
  to synthetic or redacted inputs, is not advertised as a private beta, and stores no real résumé or
  sensitive personal data.
- Provider work still runs inside the single web process. Demo profiles must use one page per source
  and low traffic. Durable admission control, cancellation, background workers, shared ingestion, and
  provider budgets remain S2/S3 work.

## Failure, recovery, and rollback

- A database or migration failure keeps Actuator health non-successful, so Render does not promote the
  new instance. The operator inspects sanitized Render logs and corrects environment configuration.
- A process restart can resume existing Spring Batch work according to its current database
  checkpoints, but free instances can be interrupted and are not an availability commitment.
- Application rollback uses Render's retained prior image after confirming that its Flyway version is
  compatible. D3 introduces no schema migration, so rollback to the pre-D3 application is data-safe.
- Neon Free is a demo datastore with limited restore history, not a backup policy. Database contents
  are disposable. Export before any provider or plan migration.

## Explicit exclusions

- Authentication, account recovery, RBAC, real-user résumé storage, malware scanning, retention,
  export/deletion, rate limiting, WAF, production secrets management, CI/CD, HA, PITR guarantees,
  alerts, load/security testing, custom domains, email, billing, and production support.
- Render background workers, cron jobs, persistent disks, or Render PostgreSQL.
- AWS resources or a commitment to a future AWS service layout.
- S0.2 remaining owner acceptance, S0.3 ranking semantics, and S1–S6 implementation.

## Completion evidence

D3 remains open until both local release checks and the remote Render/Neon smoke evidence are
recorded in `BUILD_PROGRESS.md`. Repository preparation alone is not a completed deployment.
