# JobLens V1 Engineering Standards

Status: **mandatory V1 development and review standard**, adopted on 2026-09-04.

JobLens is the product name and V1 is the current release line. This document defines how a V1 change
moves from requirement to release evidence. It does
not declare the application launch-ready. `PRODUCT_REQUIREMENTS.md` owns
product scope and launch gates; `SYSTEM_DESIGN.md` owns the architecture baseline;
`BUILD_PROGRESS.md` records observed implementation evidence.

## 1. Change lifecycle

Every implementation milestone must pass through these stages:

1. **Requirement** — identify the product requirement or defect, affected actor, expected outcome,
   out-of-scope behavior, and measurable acceptance criteria.
2. **Design** — identify module/data ownership, API and Batch boundaries, transactions, failure
   states, security/privacy effects, observability, capacity assumptions, and rollback or recovery.
3. **Implementation** — make the smallest cohesive change that satisfies the approved acceptance
   criteria while preserving compatibility and user work.
4. **Verification** — demonstrate behavior with tests and, where relevant, PostgreSQL, HTTP, Batch,
   browser, provider-contract, concurrency, performance, security, or recovery evidence.
5. **Review** — inspect correctness, design fit, data safety, security, operability, accessibility,
   and maintainability. Resolve blocking findings or record an approved exception.
6. **Release decision** — state which gate passed, which evidence was collected, remaining risks,
   and whether the change is safe to merge, deploy to development, promote to staging, or release.

Code must not begin from an unbounded suggestion list. A milestone needs an owner-approved problem
statement and acceptance criteria. Cross-cutting changes require a short design record or ADR before
implementation. Small local fixes may keep their design rationale in the milestone documentation.

## 2. Requirement and acceptance standard

A milestone definition must include:

- a stable requirement or bug ID and user-visible problem;
- actors and authorization boundary;
- inputs, outputs, success state, empty state, and recoverable/terminal failures;
- measurable functional acceptance criteria;
- relevant latency, throughput, privacy, security, accessibility, and availability expectations;
- explicit scope exclusions and dependencies;
- migration, compatibility, rollout, and rollback needs;
- evidence required to call it complete.

Words such as *fast*, *accurate*, *real-time*, *secure*, and *scalable* are not acceptance criteria
without a metric or observable behavior. Ranking quality requires a reviewed dataset and metric; it
must not be claimed from hand-picked examples alone.

## 3. Design review standard

Review the following before approving a material design:

| Concern | Required question |
| --- | --- |
| Ownership | Which module owns the behavior and data? Are dependencies directed and intentional? |
| Contract | What stable UI/API/event/Batch contract is exposed, and how is it versioned? |
| Execution | Which work is synchronous, asynchronous, scheduled, retried, or cancellable? |
| Data | What is stored, indexed, retained, migrated, exported, and deleted? |
| Transactions | What commits atomically? Where are external calls made? How is replay safe? |
| Concurrency | What prevents duplicate commands, overlapping runs, lost updates, and stale leases? |
| Failure | What does the user see? What can an operator retry, resume, quarantine, or reconcile? |
| Security | Who may perform the action? Which PII/secrets cross the boundary? What is audited? |
| Operations | Which logs, metrics, traces, alerts, dashboards, and runbooks prove health? |
| Capacity | Which declared load and provider-budget assumptions shape the design? |
| Evolution | How can the design change without premature services or irreversible coupling? |

JobLens remains a modular monolith. Use the same versioned codebase for independently scalable API
and worker roles before extracting services. A broker, search engine, or microservice requires an
observed constraint and a reviewed decision record.

## 4. Java and module coding standard

- Use Java 21 language features conservatively and keep code compatible with the recorded Spring
  Boot 4.1.1/Spring Batch 6 baseline.
- Keep behavior in the module that owns the business capability. Controllers adapt protocols;
  services enforce use cases and boundaries; repositories own persistence access; Batch
  configuration coordinates jobs and steps.
- Prefer constructor injection, immutable values, and records for suitable DTOs/value objects.
- Use descriptive domain names. Avoid generic `Util`, `Helper`, or abstraction layers without a
  concrete second use case.
- Keep methods cohesive and side effects explicit. Validate at system boundaries and preserve
  invariants inside the domain/application layer.
- Do not use Lombok. Do not add JPA merely for convenience. Do not hide failures with broad
  `catch (Exception)` blocks or return fabricated success/default values.
- External requests must have bounded timeouts, retry policy, rate/budget controls, correlation
  context, and sanitized error mapping. Never log credentials, résumé content, or unnecessary PII.
- User-facing errors use product vocabulary and stable error codes. Spring Batch `JobInstance`, SQL,
  stack-trace, and provider-secret details must not leak through normal UI/API responses.
- Public classes and non-obvious algorithms require documentation of invariants and trade-offs;
  comments must explain *why*, not restate the code.
- Formatting and static checks are mandatory. Warnings introduced by a change must be resolved or
  explicitly reviewed.

## 5. PostgreSQL and migration standard

- PostgreSQL is the source of truth; Flyway exclusively owns application schema evolution.
- Use explicit external SQL and named parameters where practical. Select only required columns and
  make ownership predicates visible in repository queries.
- Enforce durable invariants with constraints and uniqueness, not application timing assumptions.
- Add indexes from actual access paths and verify significant queries with representative plans and
  cardinality. Avoid offset pagination for large mutable result sets; prefer stable keyset cursors.
- Migrations are ordered, reviewable, forward-safe, and tested from a clean database. Destructive or
  long-running changes require staged expand/backfill/contract steps and a recovery plan.
- Backfills and reconciliation are restartable and observable. Schema changes must consider mixed
  application versions during deployment.
- Multi-user tables require explicit account/workspace ownership and negative authorization tests.

## 6. Spring Batch and asynchronous work standard

- Jobs launch explicitly with meaningful identifying parameters; automatic startup execution stays
  disabled.
- Define JobInstance/JobExecution semantics, chunk and transaction boundaries, checkpoints,
  retries, skips, restart, idempotency, and halfway-failure behavior before implementation.
- Do not perform uncontrolled external HTTP calls inside a database transaction. Persist raw input
  before normalization and retain observable rejection/failure reasons.
- Repeated commands, concurrent launches, stale executions, cancellation, and replay require a
  product-level state contract. Normal users see a run ID and product status, not Batch metadata.
- A successful restart must not duplicate durable effects. Any `ExecutionContext` key is versioned
  or documented as part of the restart contract.
- Live progress reflects committed state only. SSE may deliver progress with polling fallback; an
  in-memory emitter is never the sole durable record.

## 7. API and UI standard

- APIs define request validation, ownership, idempotency, pagination, sorting, filtering, status,
  and a stable error envelope before being treated as public contracts.
- Current endpoints remain V1-internal and unversioned until the public API strategy is approved.
  The V1 product release does not automatically mean `/v1` URLs.
- Collection endpoints use bounded page sizes and deterministic ordering. Large result navigation is
  server-side and keyset-based; client-side filtering is reserved for already bounded data.
- UI controls must expose the user’s mental model, preserve selections, handle empty/loading/error/
  partial states, and remain keyboard accessible. Provider-specific technical inputs belong in an
  advanced/admin surface unless the user must decide them.
- A UI migration must inventory every user-facing route in scope and identify its rendered owner.
  Do not call a migration complete while a primary route still renders its legacy surface, unless
  that route is an explicit, reviewed compatibility exception with an owner and removal date.
- Assisted profile setup may preselect evidence-backed suggestions to reduce effort, but activation
  must remain a visible user decision. Required readiness acknowledgements, consent, and inferred
  résumé evidence may never be silently accepted by the client.
- Country selection must provide normalized names/codes and may not demand redundant city/region
  knowledge. Skills, roles, and sectors use governed searchable vocabularies with a clear private
  custom-value path.
- Never present inferred résumé evidence as confirmed user intent.

## 8. Security, privacy, and dependency standard

- Classify account, résumé, application, feedback, audit, provider, and operational data before
  implementing storage or logging changes.
- Enforce authentication and authorization at service/repository boundaries, not only in views.
- Apply least privilege, secure sessions, CSRF/CORS/security headers, rate limits, secret rotation,
  encrypted transport/storage, PII redaction, auditability, and abuse controls appropriate to scope.
- File upload work requires size/type validation, quarantine/malware scanning, encrypted storage,
  authorized retrieval, retention, export, deletion, and backup-expiry behavior.
- New dependencies need an owner, license/security review, maintained-version rationale, and a clear
  capability benefit. Produce and scan artifacts/SBOMs before a public release.
- Threat-model review is required for identity, uploads, external redirects, support/admin access,
  provider callbacks, data export/deletion, and any new trust boundary.

## 9. Verification matrix

Choose tests by risk; a compile-only check is insufficient for behavioral work.

| Change | Minimum evidence |
| --- | --- |
| Pure logic | Focused unit tests including boundaries and negative cases |
| SQL/schema/repository | PostgreSQL Testcontainers, clean Flyway migration, constraints and ownership cases |
| Batch | Job/step integration, idempotent rerun, failed execution, restart/checkpoint and skip evidence |
| Provider adapter | Mock HTTP contract, paging, mapping, timeout, retry, quota/auth and malformed response cases |
| Controller/API | Validation, status/error contract, ownership and serialization tests |
| UI flow | Controller integration plus browser/manual evidence for state, accessibility and error paths |
| Concurrency/run control | Parallel command, duplicate/idempotency, stale lease and recovery tests |
| Performance-sensitive | Representative data, query plans, load/soak result against an explicit target |
| Security/privacy | Negative authorization, threat cases, log/redaction and export/deletion evidence |
| Operations/release | Health/readiness, metrics/alerts, migration, rollback and restore/runbook rehearsal |

The default repository checks remain `./mvnw clean test` and `git diff --check`. Formatting uses the
configured Spotless task. Tests requiring PostgreSQL use PostgreSQL/Testcontainers rather than H2.

## 10. Review gates

| Gate | Approval evidence |
| --- | --- |
| Product | Requirement, actor, acceptance criteria and priority agreed |
| Architecture | Boundary, data/transaction model, failure handling and capacity reviewed |
| Data/security | Ownership, privacy, threats, migration and retention reviewed where affected |
| Code | Maintainability, correctness, compatibility and dependency review complete |
| Test | Required verification matrix executed with actual results recorded |
| Operability | Metrics/logs/alerts/runbook and support behavior exist where affected |
| Release | Rollout/rollback plan, unresolved risks and target environment explicitly accepted |

A reviewer must be able to trace requirement → design decision → code → tests → observed evidence.
Self-review is required before handoff even when a second human reviewer is not yet assigned.

## 11. Definition of Done

A V1 item is complete only when:

- its acceptance criteria are satisfied and linked to evidence;
- code and data changes follow the approved design and module ownership;
- success, empty, partial, error, concurrency, and restart paths relevant to the change are handled;
- tests pass at the required levels without disabling real infrastructure semantics;
- security/privacy/accessibility/operability impacts have been reviewed;
- migrations, compatibility, rollout, rollback, and support notes are complete where applicable;
- API/UI documentation and `BUILD_PROGRESS.md` reflect only verified behavior;
- no credentials, personal résumé data, debug artifacts, raw framework errors, or unexplained skipped
  records are introduced;
- the diff passes formatting/static checks and a final evidence-backed review;
- remaining risks and deferred work are explicit, and one conventional commit closes the milestone.

“Implemented” is not synonymous with “done,” and “V1” is not synonymous with “production-ready.” A
release gate passes only when its required evidence exists.

## 12. Exceptions and review severity

- **Blocker:** data loss/exposure, authorization bypass, secret leakage, irreversible unsafe
  migration, or materially false release evidence. It must be fixed before merge or promotion.
- **Major:** incorrect user outcome, broken restart/idempotency, unbounded provider behavior, missing
  required test, or architecture violation. It must be fixed or receive an explicit time-bounded
  owner-approved exception.
- **Minor:** maintainability, consistency, accessibility, or documentation issue that does not
  invalidate acceptance. Record an owner and target milestone if deferred.

Exceptions state the violated rule, reason, risk, compensating control, owner, expiry date, and
removal milestone. “For V1” is not a permanent exception rationale.
