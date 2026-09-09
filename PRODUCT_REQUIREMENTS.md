# JobLens Product Requirements Baseline

Status: **JobLens V1 startup baseline**, recorded on 2026-09-04. JobLens is the product name; V1 is
the first release line. This document is authoritative for product
scope and launch readiness. `SYSTEM_DESIGN.md` translates these requirements into architecture;
`ENGINEERING_STANDARDS.md` defines mandatory development/review gates; `BUILD_PROGRESS.md` remains
historical implementation evidence.

## 1. Product definition

JobLens is a production SaaS product for job seekers. It turns a reviewed candidate profile into
fresh, explainable job discovery, role-aware ranking, application tracking, and market insight across
legitimate public sources.

The product is not primarily a Spring Batch learning exercise and must not be described as a
personal-only finished application. Spring Batch is one implementation tool for durable ingestion and
reprocessing. The current repository contains the **JobLens V1** release implementation. It has a functionally
rich architecture foundation, but it is not yet a launch-ready corporate-grade service.

“Corporate-grade” means authenticated multi-user operation, privacy and security controls, reliable
asynchronous execution, measurable service levels, support/admin tooling, deployability, backups,
observability, and incident recovery. It does not change JobLens into an employer ATS or recruiting
CRM. That would be a separate product decision.

## 2. Users and actors

| Actor | Primary need | Launch scope |
| --- | --- | --- |
| Job seeker | Maintain a profile, discover and rank jobs, understand evidence, track applications | Required |
| Support operator | Inspect sanitized user/run state, help recover failed work, process deletion requests | Required |
| Platform administrator | Manage providers, quotas, taxonomy/ranking versions, incidents, and feature rollout | Required |
| Anonymous visitor | View marketing/help content and begin registration | Required outside the current V1 application UI |
| Employer/recruiter | Publish or manage vacancies | Not in scope unless separately approved |

One authenticated account owns one private candidate workspace at launch. The data model may later
support multiple profiles or organization tenants, but those are not launch assumptions and must not
be prebuilt without a use case.

## 3. Planning envelope — estimate, not measured demand

These figures are capacity assumptions used to expose architectural gaps. They are not a sales
forecast or a claim that the current application has passed this load.

| Stage | Registered accounts | DAU | Peak active sessions | Discovery commands/day | Active/historical normalized jobs |
| --- | ---: | ---: | ---: | ---: | ---: |
| Private beta | 5,000 | 500 | 100 | 1,000 | 500,000 |
| Year-one target | 50,000 | 5,000 | 750 | 10,000 | 2,000,000 |
| Design stress point | 250,000 | 25,000 | 3,000 | 50,000 | 10,000,000 |

Current verified local evidence is only 14 anonymous workspaces, 1,528 raw postings, 1,527 normalized
jobs, 22 workspace search runs, 47 Batch executions, two applications, and a 36 MB PostgreSQL
database. This proves functionality, not capacity.

Capacity assumptions for the year-one target:

- median two target roles, three markets, three pages per discovery command;
- approximately 18 logical provider page requests per command under the current unshared model;
- approximately 180,000 provider page requests/day at 10,000 commands/day before retries or ATS
  enrichment;
- peak API traffic target of 200 requests/second, dominated by reads and progress/result refreshes;
- peak accepted discovery command target of five/second, with explicit admission control rather than
  five synchronous provider crawls;
- up to 25 million workspace sightings and candidate-score projections before retention/partitioning
  must be revisited.

Provider quota and source licensing are expected to fail before compute at that request volume.
JobLens therefore requires shared, deduplicated job ingestion by source/market/query fingerprint,
provider-specific budgets, and private candidate ranking over the shared corpus. A user command may
request freshness, but it must not blindly repeat equivalent external calls already satisfied within
a bounded freshness window.

### Product decisions still requiring owner approval

The baseline fixes the engineering planning envelope; it does not invent unresolved business policy.

| Decision | Why it matters | Current state |
| --- | --- | --- |
| Initial launch countries and languages | Provider contracts, localization, support hours and data quality | Ten provider-capable markets exist in the V1 implementation; launch subset not selected |
| Free, subscription, employer-sponsored or mixed model | Request budgets, billing, entitlements and unit economics | Not selected |
| Identity provider and login methods | Account migration, recovery, MFA and operating cost | Not selected |
| Provider commercial agreements and per-user budgets | Legal access, quota, refresh frequency and cost | Not validated for startup volume |
| Résumé/raw-job/log/backup retention periods | Privacy, storage cost, deletion and recovery behavior | Not selected |
| Support coverage and incident ownership | SLO feasibility and admin tooling | Not staffed or selected |
| Notification channels and consent | Delivery vendor, quiet hours, retries and privacy | Not selected |
| Accessibility and localization target | UI acceptance testing and content design | Must be defined before public launch |

Until these are approved, code may expose configuration seams and evidence but must not silently bake
in a permanent business policy.

## 4. Product outcomes and success measures

Launch outcomes:

1. A user can create a recoverable private account, review a profile, and intentionally select role
   and market direction.
2. A discovery command is acknowledged quickly, progresses visibly, and produces fresh results or a
   specific recoverable explanation.
3. Recommended results are explainable and measurably more relevant than newest-only results.
4. A user can save, track, and act on applications without losing history.
5. Operators can detect provider, queue, database, and ranking regressions before users report them.

Initial product measures, with targets finalized after beta instrumentation:

- onboarding completion rate;
- time from registration to first usable result;
- discovery success/partial/failure rate by provider and market;
- Precision@10 from explicit Fit/Maybe/Not-fit labels, segmented by role family and market;
- result-open, save, and application-transition rates;
- seven-day and thirty-day retained users;
- provider requests and cost per successful discovery command;
- support incidents, recovery time, and deletion/export completion time.

## 5. Functional requirements and current status

Status meanings: **FIXED** means implemented and verified for the current V1 scope;
**PARTIAL** means useful implementation exists but launch requirements are incomplete; **MISSING**
means no adequate implementation exists. Fixed does not imply year-one load validation.

| ID | Requirement | Status | Current evidence or gap |
| --- | --- | --- | --- |
| FR-01 | Versioned résumé/profile onboarding with user review | PARTIAL | PDF/DOC/DOCX parsing, draft activation, readiness findings, and basic skill/role review work; searchable React role/sector assistance, measured keyword coverage, a polished progressive review, original bytes, malware scanning, authenticated ownership, and deletion/export remain open |
| FR-02 | Explicit role intent separate from inferred résumé evidence | FIXED | One to three ordered roles and persisted `role-intent-v1` provider queries |
| FR-03 | Multi-market preference management | PARTIAL | Normalized targets, provider capability catalogue, and responsive multi-row React management work; normalized sector assistance, authenticated ownership, launch-market selection, and measured market quality remain open |
| FR-04 | Legitimate job ingestion with untouched raw evidence | PARTIAL | Adzuna, optional Jooble, and bounded Greenhouse/Lever enrichment work; commercial terms, quotas, shared ingestion, and broader reliable coverage are unresolved |
| FR-05 | Restartable normalization, skill extraction, and duplicate processing | FIXED | PostgreSQL/Flyway/Spring Batch pipeline is deterministic, observable, and tested at functional scale |
| FR-06 | Explainable role-aware ranking | PARTIAL | Universal policy and four overlays persist per-role evidence; relevance is not yet calibrated and cross-role false positives are open |
| FR-07 | Search results exploration | PARTIAL | The dashboard returns up to 25 eligible workspace-sighted jobs ordered by candidate score, but has no minimum recommendation threshold or separate low-confidence/new-results treatment; backend filters, stable user-selected sorts, keyset pagination, grouping, and saved-state explorer are also missing |
| FR-08 | Application and follow-up lifecycle | PARTIAL | Deterministic lifecycle/history/follow-ups work; reminders, notification delivery, account ownership, and support recovery are incomplete |
| FR-09 | Market insights | PARTIAL | Weekly aggregate job exists; product definition, tenant/privacy boundary, scheduling, and useful empty-state/data freshness require completion |
| FR-10 | Live run progress and recovery | MISSING | Source-run records exist, but no supported real-time channel, admission control, safe active-run UX, stale-run recovery, or cancellation contract exists |
| FR-11 | Authentication, account recovery, and authorization | MISSING | Anonymous UUID cookie is not an authenticated multi-user boundary; no Spring Security dependency or RBAC exists |
| FR-12 | User data export and deletion | MISSING | Cascades exist for some workspace rows, but no owned export/deletion workflow, retention policy, audit, or backup-expiry contract exists |
| FR-13 | Support/admin console | MISSING | Swagger and raw Batch inspection are developer tools, not an audited support surface |
| FR-14 | Notifications | MISSING | Follow-up rows exist; email/push/in-app delivery, preferences, quiet hours, retries, and idempotency are absent |
| FR-15 | Product analytics and feature rollout | MISSING | No privacy-aware event model, funnels, experiments, feature flags, or rollout controls |

## 6. Real-time interaction contract

JobLens is not required to make external providers synchronous. “Real-time” means the user receives
immediate command acknowledgement and current state while durable asynchronous work continues.

| Operation | Target behavior | Execution boundary |
| --- | --- | --- |
| Read dashboard/filter results | Current committed data, p95 below 300 ms at year-one load | Synchronous API/read model |
| Save profile/application/feedback | Confirmed mutation, p95 below 500 ms | Synchronous transaction |
| Start discovery | Return accepted run ID and current state below two seconds | API command plus durable queue/admission control |
| View progress | Update within five seconds of a committed provider/processing transition | SSE initially; polling fallback; WebSocket only if two-way needs emerge |
| View discovered jobs | Incrementally expose safe committed results; no partial transaction reads | Read model refreshed from ingestion/ranking events |
| Cancel/retry | Explicit state transition with idempotent semantics | Asynchronous command, audited |

Spring Batch remains appropriate for imports, provider-page workflows, normalization/reprocessing,
taxonomy updates, aggregates, and bulk scoring. It must sit behind product-level run commands and
states; normal users must never receive raw `JobInstance`/`JobExecution` errors.

## 7. Non-functional requirements

### Reliability and performance

| ID | Year-one target | Current status |
| --- | --- | --- |
| NFR-01 | 99.9% monthly availability for authenticated API and dashboard, excluding declared maintenance | MISSING; single local container |
| NFR-02 | API read p95 <300 ms and mutation p95 <500 ms at 200 requests/second | UNVERIFIED; no load test |
| NFR-03 | Discovery command acknowledgement <2 s; progress freshness <5 s | MISSING |
| NFR-04 | 95% of admitted default discovery commands reach terminal state within 10 minutes when providers respond | UNVERIFIED |
| NFR-05 | RPO ≤5 minutes and RTO ≤60 minutes for primary regional deployment | MISSING; no managed backup/PITR or recovery drill |
| NFR-06 | No duplicate external request for an equivalent fresh query fingerprint inside its provider cache window | MISSING |
| NFR-07 | Resume upload acknowledges the action visually within 100 ms, always shows parsing/success/partial/failure state, and meets a benchmarked completion target on the supported 5 MB limit | PARTIAL; a loading label exists, but staged progress and a representative latency benchmark do not |

### Security and privacy

- Authenticated identity using a supported OIDC/OAuth2 or passwordless provider; secure server-side
  session lifecycle; MFA required for administrators.
- Authorization roles at minimum `USER`, `SUPPORT`, and `ADMIN`; support access is explicit,
  time-bounded where feasible, and audited.
- TLS in transit, managed encryption at rest, secrets from a secret manager, secure cookies, CSRF
  protection, rate limiting, upload size/type validation, malware scanning, dependency/container
  scanning, and security headers.
- Strict account/workspace ownership on every user-facing query. PostgreSQL row-level security may be
  added as defense in depth after the authenticated ownership model is defined; it is not a substitute
  for application authorization.
- Defined retention for raw provider payloads, derived jobs, résumé objects, extracted evidence,
  inactive accounts, logs, backups, and analytics events.
- User-accessible export and deletion, with auditable completion and documented backup expiry.
- Threat model, abuse cases, incident response, vulnerability handling, and privacy/legal review must
  be completed before public launch. This document does not substitute for legal advice.

### Operability

- Separate development, staging, and production environments with infrastructure as code.
- Automated build/test/security gates, immutable artifacts, controlled migrations, progressive
  deployment, rollback/runbook, and feature flags for risky provider or ranking changes.
- Structured logs with correlation IDs, metrics, distributed traces, dashboards, and alerts for API,
  queue, Batch, database, provider, and ranking health.
- Managed PostgreSQL with high availability, connection pooling, PITR backups, tested restores, query
  monitoring, and capacity alarms.
- Per-provider concurrency, rate, daily request, cost, retry, and circuit-breaker budgets.
- Audited admin operations for run cancellation/recovery, provider disablement, taxonomy/pack
  activation, account support, and data deletion.

## 8. Architecture direction fixed by this baseline

1. Start as a modular monolith, not a network of premature microservices.
2. Build one deployable codebase that can run as independently scaled API and worker process roles.
3. Keep PostgreSQL as the transactional system of record and Flyway as schema owner.
4. Store résumé objects in encrypted object storage; keep metadata, ownership, and hashes in
   PostgreSQL.
5. Separate shared provider ingestion/job identity from private candidate intent, sightings, ranking,
   feedback, and application state.
6. Add a durable command/admission-control boundary and transactional outbox before relying on live
   events. A managed broker is justified only when PostgreSQL/outbox throughput or delivery isolation
   is measured as insufficient.
7. Use SSE for server-to-browser progress first, with polling fallback. Do not use WebSockets merely
   to appear real-time.
8. Preserve deterministic, versioned, explainable processing. ML/LLM assistance may be evaluated only
   behind an explicit policy, privacy review, offline evaluation, and deterministic fallback.
9. Provider access must remain legitimate. Do not scrape LinkedIn, Indeed, Google, JobStreet, or SEEK.
10. Microservice extraction requires measured scaling, fault-isolation, deployment, or team-ownership
    pressure; user count alone is not sufficient.

## 9. Launch-readiness summary

### Foundation already worth keeping

- Java 21/Spring Boot modular monolith and PostgreSQL/Flyway schema discipline.
- Raw-before-normalized source pipeline with bounded retries and observable provider failures.
- Spring Batch metadata, checkpoints, restart, idempotency, and transactional chunk processing.
- Deterministic normalization, skills, duplicate evidence, versioned profile intent, and ranking
  explanations.
- Workspace-scoped data model across profiles, sightings, scores, views, applications, and follow-ups.
- Functional PostgreSQL Testcontainers and provider contract tests.
- Docker image, health endpoint, REST/OpenAPI, and Thymeleaf product flow.

### Not fixed and blocks public startup launch

- authenticated identity, authorization, recovery, and tenant security;
- shared/provider-budgeted ingestion instead of repeated per-user crawling;
- asynchronous admission control, real-time progress, cancellation, and stale-run recovery;
- the open zero-result, résumé extraction, location, concurrency, and cross-role ranking defects;
- Job Explorer filters/sorts/keyset pagination and measurable ranking feedback;
- secure original résumé storage, malware scanning, retention, export, and deletion;
- production CI/CD, environments, managed database, HA, backups, disaster recovery, and runbooks;
- metrics/traces/log aggregation/alerts and audited support/admin operations;
- load, soak, security, failure, and restore testing at declared targets;
- provider commercial/quota validation and sustainable unit economics.

### Not required before a controlled private beta, but required before broader scale

- notification delivery and preference center;
- full product analytics and experimentation controls;
- advanced search infrastructure beyond PostgreSQL, only if indexed PostgreSQL misses measured SLOs;
- service extraction, only when a real trigger is crossed;
- employer/recruiter workflows, billing tiers, and organization workspaces unless product strategy
  explicitly selects them.

## 10. Delivery gates

No document may call JobLens “production ready,” “corporate ready,” or “complete” until evidence
exists for the relevant gate.

1. **G0 — Requirements and threat model:** product boundary, actors, SLOs, data classification,
   provider economics, and abuse cases approved.
2. **G1 — Authenticated private beta:** identity/RBAC, secure sessions, account ownership, privacy
   workflow, run admission/recovery, critical bug fixes, production-like staging, CI/CD, monitoring,
   backups, and restore drill.
3. **G2 — Public launch:** load/security tests, provider budgets/contracts, real-time progress,
   support/admin tooling, incident/on-call runbooks, deletion/export, and measured ranking quality.
4. **G3 — Year-one scale:** shared ingestion effectiveness, database/query SLOs, retention and
   partitioning, worker autoscaling, cost controls, and disaster-recovery evidence.

Delivery gates control environment promotion; they do not prevent evidence-backed correction of
known defects. S0.1 Discovery Execution Safety completed on 2026-09-08; no subsequent defect or
launch milestone is automatically selected. G0 remains open until its threat-model,
provider-economics, data-classification, and owner-decision evidence is approved. Requirement
documentation alone does not authorize authentication, storage, infrastructure, or
service-splitting changes.
