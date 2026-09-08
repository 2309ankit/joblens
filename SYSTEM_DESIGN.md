# JobLens V1 System Design Baseline

Status: **JobLens V1 architecture baseline**, recorded on 2026-09-04. This replaces the former
personal-machine operating envelope. [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) is the
authoritative product and launch-readiness baseline;
[ENGINEERING_STANDARDS.md](ENGINEERING_STANDARDS.md) defines its design and review gates. This
document describes how the system should evolve from its verified development baseline without
pretending missing production capabilities already exist.

## 1. System boundary

JobLens is a multi-user job-seeker intelligence SaaS. It accepts reviewed candidate intent, acquires
jobs from legitimate sources, preserves raw evidence, normalizes and deduplicates a shared job corpus,
ranks candidate-visible jobs privately, and supports application/follow-up workflows.

It is not an employer ATS, automatic application bot, portal credential vault, or unauthorized
scraper. “Real-time” means immediate command acknowledgement, observable asynchronous progress, and
incrementally fresh committed results. It does not mean holding an HTTP request open while public
providers are crawled.

## 2. Current versus target architecture

| Concern | Verified current implementation | Startup target | State |
| --- | --- | --- | --- |
| Deployment | One Dockerized Spring Boot process plus one PostgreSQL container | Stateless API replicas and independently scalable worker replicas from one codebase | PARTIAL |
| Identity | Opaque anonymous workspace UUID cookie | Recoverable authenticated account, secure session, RBAC, audited support access | MISSING |
| Tenancy | Workspace IDs and ownership joins on user data | Authenticated account-to-workspace ownership on every command/query, isolation tests and defense in depth | PARTIAL |
| Interactive API | MVC/REST/Thymeleaf, synchronous reads and small writes | SLO-backed API behind TLS/load balancer with rate limiting and safe errors | PARTIAL |
| Long work | Spring Batch launched directly from web flows | Durable product command, admission control, queued execution, cancellation/recovery, worker capacity | PARTIAL |
| Live progress | Dashboard refresh and persisted run inspection | SSE within five seconds of committed changes, polling fallback | MISSING |
| Job acquisition | Per-workspace source profiles and sequential discovery | Shared query-fingerprint cache/corpus, per-provider budgets, incremental ingestion | PARTIAL |
| Intelligence | Deterministic normalized pipeline, duplicates, skills, role-aware scoring | Same algorithms at scale with reviewed calibration, partitioning, backfills and version governance | PARTIAL |
| Data | Local PostgreSQL volume, Flyway | Managed HA PostgreSQL, pooled connections, PITR, restore tests, retention/partitioning | PARTIAL |
| Résumé storage | Metadata/hash only; original bytes discarded | Encrypted object storage, malware scan, retention, export/deletion | MISSING |
| Operations | Actuator health, Batch metadata, source-run rows | Metrics, traces, centralized logs, alerts, SLOs, admin console, incident runbooks | PARTIAL |
| Delivery | Local Maven and Docker commands | CI/CD, staging/production, immutable image, security gates, progressive rollout | MISSING |

The modular monolith remains the correct starting architecture. “Monolith” describes the code and
transaction boundary, not a requirement to run exactly one process. API and worker runtime roles can
scale independently while sharing versioned modules and PostgreSQL.

## 3. Planning workload

The year-one planning target is 50,000 registered accounts, 5,000 DAU, 750 peak active sessions,
10,000 discovery commands/day, 200 peak API requests/second, five peak accepted discovery
commands/second, two million shared normalized jobs, and up to 25 million candidate sightings/score
projections. These are design assumptions, not load-test results.

With a median two roles × three markets × three pages, the current per-user model implies 18 provider
page calls per discovery command or roughly 180,000/day at the year-one target before retries. This is
not an acceptable quota or cost model. Shared acquisition is therefore a functional architecture
requirement, not a later performance optimization.

Current local evidence—14 workspaces, about 1,500 jobs, 22 search runs, 47 Batch executions, and a
36 MB database—validates behavior only.

## 4. Target logical architecture

```text
Browser / mobile web
        │ HTTPS, authenticated session
        ▼
CDN / WAF / load balancer
        │
        ▼
Stateless JobLens API replicas ───────────────┐
  profile, explorer, applications             │ SSE / polling
  commands, authorization, rate limits        │
        │                                      │
        ├─ synchronous transactions            │
        └─ durable command + outbox ───────────┘
                       │
                       ▼
              JobLens worker replicas
              Spring Batch / bounded tasks
                       │
          ┌────────────┴────────────┐
          ▼                         ▼
 provider adapters          deterministic intelligence
          │                         │
          ▼                         ▼
 raw landing → shared normalized job corpus → private sightings/ranking
          │                         │
          └──────── PostgreSQL ─────┘
                       │
           object storage for résumé objects

Metrics / traces / logs / alerts observe API, workers, database, outbox and providers.
```

The first production deployment should remain one repository and one versioned application artifact
with runtime profiles:

- **API role:** accepts authenticated commands, serves explorer/profile/application reads and writes,
  and streams authorized progress. It does not perform uncontrolled provider calls.
- **Worker role:** claims admitted work, runs provider and intelligence steps with bounded concurrency,
  and publishes transactional state changes.
- **Operator role:** explicit restricted endpoints/commands for taxonomy, backfill, pack activation,
  recovery, and support actions. It is not exposed as the normal user API.

## 5. Shared ingestion and private ranking

The target separates reusable provider facts from candidate-private decisions.

```text
User intent
  → normalized query fingerprint (source, market, terms, filters, version)
  → fresh shared result exists? ─ yes → attach candidate sighting
                              └ no  → admit provider work within budget
                                       → raw immutable evidence
                                       → shared normalized/deduplicated job
  → private candidate role scores and reasons
  → private feedback/application state
```

Shared data includes provider identity, raw payload/hash, normalized posting, extracted global skills,
duplicate evidence, source freshness, and public market aggregates. Private data includes account,
résumé/profile, selected intent, custom taxonomy values, search history, sightings, candidate scores,
feedback, views, applications, and follow-ups.

Equivalent provider queries need a persisted fingerprint, freshness window, lease, request budget,
and subscriber list so concurrent users do not trigger duplicate crawls. Cache reuse must remain
observable: the run should state whether data was fetched, refreshed, or reused. Candidate ranking
must never become shared merely because ingestion is shared.

## 6. API, Batch and real-time boundaries

| Operation | Interface | Execution model |
| --- | --- | --- |
| Register/login/logout/recover | Auth API | Synchronous security transaction plus provider flow |
| Upload résumé | API | Stream validation and object quarantine; asynchronous scan/parse when needed |
| Review/activate profile | API | Synchronous owned transaction |
| Filter/sort/page jobs | API | Indexed PostgreSQL query with stable keyset cursor |
| Start discovery | Command API | Persist command/idempotency key and return run ID in under two seconds |
| Provider acquisition | Worker/Batch | Bounded asynchronous work outside uncontrolled DB transactions |
| Normalize/deduplicate/extract/rank | Worker/Batch | Chunked, restartable, versioned and idempotent |
| Progress | SSE, polling fallback | Publish only after state transaction commits |
| Save/view/apply/feedback | API | Synchronous owned transaction and outbox event where needed |
| Taxonomy/backfill/aggregates | Operator Batch | Explicit, audited, restartable |
| Notification delivery | Worker | Outbox-driven, retryable, idempotent |

Product run states must be stable and framework-neutral, for example `QUEUED`, `RUNNING`, `PARTIAL`,
`COMPLETED`, `FAILED`, `CANCELLING`, and `CANCELLED`. Spring Batch JobInstance, JobExecution and
StepExecution IDs remain operator evidence; they are not user-facing error text.

SSE is the initial push mechanism because progress is server-to-client and HTTP-friendly. Polling is
the compatibility fallback. WebSocket adoption requires a genuine bidirectional interaction need.

## 7. Consistency and idempotency

- PostgreSQL is the transactional system of record; Flyway remains the sole schema owner.
- User commands accept an idempotency key. Repeated delivery returns the original command/run rather
  than launching duplicate work.
- Provider request leases prevent equivalent active fetches. Lease expiry and takeover are explicit
  and observable.
- Raw identity remains provider source plus external job ID. Payload hashes distinguish unchanged and
  changed content.
- A transaction writes domain state and an outbox event atomically. Consumers are idempotent and keep
  delivery checkpoints.
- Batch chunks commit raw/derived state and checkpoint consistently. Restart never assumes an
  uncommitted external request succeeded; provider identities and hashes make replay safe.
- Candidate score identity includes candidate, job, policy version, role and pack version. Best-score
  projections remain rebuildable from per-role evidence.
- Results may be eventually consistent while a run progresses. APIs never expose uncommitted data and
  report the result snapshot/freshness version used.

## 8. Data model and indexing direction

Preserve the current entity chain but replace anonymous identity with account ownership:

```text
Account ── owns ── Workspace
  └─ Profile versions / résumé objects / target roles / markets
      └─ Search commands and subscriptions
          └─ Workspace sightings / private role scores / feedback
              └─ Applications / transitions / follow-ups

Provider query fingerprint
  └─ Source fetch executions
      └─ Raw postings
          └─ Shared normalized jobs / skills / duplicate evidence
```

Before year-one scale, add indexes based on actual explorer predicates and query plans, time-based
retention for raw/run/event data, and partitioning only for measured large append-heavy tables such as
raw postings, sightings, score history, audit events, and outbox deliveries. Do not introduce a search
engine until PostgreSQL full-text/indexed queries fail the declared relevance or latency target under
representative load.

## 9. Security and privacy architecture

The anonymous UUID cookie is a V1 development convenience, not authentication. Production requires:

- externalized or standards-based identity, secure server-side session/token validation, rotation,
  logout/revocation and account recovery;
- RBAC for user/support/admin and explicit ownership checks at service/repository boundaries;
- HTTPS-only Secure/HttpOnly/SameSite cookies, CSRF protection, security headers, CORS policy, rate
  limiting and abuse controls;
- secret-manager credentials with rotation; no `.env` in production;
- encrypted object storage with quarantine, malware scan, content validation and authorized access;
- encryption at rest/in transit, minimal PII in logs, audit events and redaction;
- export, deletion, retention and backup-expiry workflows;
- dependency/image scanning, SBOM, threat model, penetration/security testing and incident response.

PostgreSQL row-level security is a possible defense-in-depth layer after account/tenant semantics are
fixed. It does not replace application authorization or ownership tests.

## 10. Reliability and operations

Year-one design objectives:

- authenticated API/dashboard availability: 99.9% monthly;
- read p95 below 300 ms and mutation p95 below 500 ms at 200 requests/second;
- discovery command acknowledgement below two seconds and progress age below five seconds;
- admitted default runs terminal within ten minutes at p95 when providers respond;
- RPO at most five minutes and RTO at most sixty minutes.

Required production controls are managed HA PostgreSQL, PITR backups, restore drills, connection-pool
limits, multi-replica health/readiness, graceful shutdown, worker leases, queue-depth autoscaling,
provider circuit breakers/budgets, structured logs, correlation IDs, metrics, traces, SLO dashboards,
alerts, on-call ownership and incident runbooks.

The existing Actuator health endpoint, Batch metadata and source-run tables are useful signals but do
not satisfy this operating model by themselves.

## 11. Scale path and service-extraction triggers

Scale the modular monolith in this order:

1. Index and measure PostgreSQL queries; add keyset pagination.
2. Split the same artifact into API and worker runtime roles.
3. Add provider admission control, shared query reuse, leases and an outbox.
4. Scale API and worker replicas independently.
5. Add table partitioning/retention when measured write volume or maintenance requires it.
6. Add a broker when outbox polling cannot meet throughput/isolation SLOs.
7. Add a search engine only when PostgreSQL cannot meet measured search needs.
8. Extract a microservice only when one module has a distinct scaling bottleneck, fault boundary,
   data ownership, deployment cadence, or owning team.

Potential future extraction candidates are provider acquisition, notification delivery, and search
indexing. None is authorized merely by the estimated user count.

## 12. Delivery sequence

The verified M3.2 code remains the implementation baseline. Startup work should be selected in small
evidence-backed milestones:

1. maintain the requirements/architecture baseline and complete the remaining G0 threat-model,
   provider-economic, data-classification, and owner-decision evidence before environment promotion;
2. retain the completed S0.1 discovery execution-safety evidence, then repair the remaining recorded
   V1 defects through separately selected S0.2 onboarding correctness and S0.3 cross-role ranking
   correctness checkpoints; defect stabilization may proceed while G0 approval evidence is completed;
3. implement authenticated account ownership, RBAC and migration from anonymous workspaces;
4. introduce product-level run commands, admission control, safe concurrency/recovery and SSE status;
5. build shared query-fingerprint ingestion and provider budgets;
6. complete Job Explorer backend pagination/filtering and feedback calibration;
7. add secure résumé object lifecycle and user export/deletion;
8. establish CI/CD, production-like staging, managed data services, observability, backups and restore
   evidence;
9. run load, soak, security and failure tests against the declared beta gate.

These steps are sequencing guidance, not authorization to implement multiple milestones at once.
