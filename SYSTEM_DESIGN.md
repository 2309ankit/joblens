# JobLens System Design Baseline (D0)

Status: baseline v1, recorded after M2.7 and updated through M3.2. This is the design contract for the
current personal-scale modular monolith. Values labelled **target** are design objectives, not
load-test claims.

## 1. Problem and boundary

JobLens helps one job seeker turn a reviewed resume and job preferences into an explainable stream
of public job opportunities, then track applications and follow-ups. It is not a recruiting system,
an employer ATS, a portal scraper, or an autonomous application bot.

Primary actor: a job seeker using one browser workspace. Secondary actor: an operator importing
taxonomies, inspecting Batch runs, or launching lower-level jobs.

### Prioritized functional requirements

1. A job seeker can upload, review, version, and activate a candidate profile.
2. The system can discover legitimate public postings, preserve raw provider evidence, normalize,
   deduplicate, and rank them for that profile.
3. The job seeker can inspect results and run evidence, then track applications and follow-ups.

Supporting functions include CSV import, taxonomy import, weekly market aggregation, Swagger, and
outbound portal search links. They are not primary product requirements.

### Non-goals

- LinkedIn, Indeed, JobStreet, SEEK, or Google scraping
- Automatic job applications or portal credential storage
- Proprietary ATS reproduction or opaque candidate-quality scoring
- Employer/end-client inference without evidence
- Microservices, Kafka, or distributed execution without measured need

## 2. Operating envelope and parameters

The current deployment is a single Spring Boot process and one PostgreSQL database on a personal
machine. It favors correctness, auditability, and restartability over concurrency.

| Dimension | Current limit/default | Baseline decision |
| --- | --- | --- |
| Active human usage | One primary operator | Personal deployment, best-effort availability |
| Identity | One anonymous cookie per workspace | No recovery or cross-device guarantee |
| Resume upload | PDF/DOC/DOCX, 5 MB, at least 80 readable characters | Synchronous API operation |
| Search markets | Maximum 10 per profile | Independent provider profile/checkpoint per market |
| Search intent | One primary plus up to two additional target roles | Ordered and explicitly selected by the user |
| User-selected pages | 1-20 | Provider/global caps still apply |
| Adzuna fetch cap | Default 5 pages × 20 results/profile | 100 results/profile/run before provider count termination |
| Adzuna freshness | 30 days | Old landed Adzuna rows excluded from user results |
| External timeout/retry | Usually 10 seconds, 3 attempts, 250 ms initial backoff | Bounded failure, no unbounded HTTP wait |
| Intelligence chunk | 20 records | One transaction per committed chunk |
| ESCO import | 250 records/page, 30-second timeout, 3 attempts | Explicit restartable operator job |
| Fuzzy thresholds | 75 possible, 90 likely | Heuristics pending reviewed calibration |
| Resume readability | 0-100 `readability-v1` | Parser readability only; review severity requires acknowledgement |
| Job ranking | 0-100 `universal-v1` per selected role | Four initial versioned overlays add evidence without excluding other roles |
| Storage retention | Indefinite until workspace/data deletion | Known policy gap; not a durability promise |

Design targets for the current envelope:

- At most one logical Find-jobs JobInstance for the same workspace and identifying parameters.
- Interactive reads should remain below 500 ms at 10,000 candidate-visible jobs (**target; not load
  tested**).
- A default Find-jobs run should normally complete within five minutes when providers respond
  (**target; provider latency and quota can dominate**).
- Recovery point is the last committed PostgreSQL transaction or Batch chunk. Recovery is manual
  restart; there is no high-availability or automated failover target.
- No committed profile, application transition, or completed chunk should be silently lost.

Capacity estimates are performed only when they affect a decision:

```text
provider requests/run ≈ active provider profiles × pages attempted
raw rows/day          ≈ workspaces × runs/day × profiles/run × results/profile
fuzzy candidates      ≈ blocked pairs, not all N² pairs after cheap blocking
```

Provider quota is expected to become the first constraint. Database volume and fuzzy-pair memory are
the next likely constraints; neither justifies distributed infrastructure at the current envelope.

## 3. Core entities and ownership

```text
Workspace
  └─ Resume metadata
      └─ Profile Version
          ├─ Readiness Assessment / Findings
          ├─ Skill and Role Suggestions
          ├─ Reviewed Skills / Roles / Preferences
          └─ Search Targets
              └─ Provider Search Profiles
                  └─ Source Fetch Runs
                      └─ Raw Job Postings
                          └─ Normalized Jobs
                              ├─ Duplicate Evidence
                              ├─ Workspace Sightings
                              └─ Candidate Scores
                                  ├─ Best-role projection / reasons
                                  └─ Per-target-role scores / reasons

Candidate Profile + Normalized Job
  └─ Application
      ├─ Transition History
      └─ Follow-up
```

Workspace-owned state: profile versions, private taxonomy additions, preferences, targets, sightings,
scores, views, applications, and follow-ups. Shared state: normalized provider postings, global
taxonomy, exact/fuzzy evidence, and weekly market projections. Every user-facing shared row is reached
through a workspace-owned sighting or candidate ownership check.

## 4. Interfaces: API versus Batch

The API is the command/query boundary. Batch is the durable execution mechanism for bulk,
multi-step, replayable work.

| Operation | Interface | Execution model | Reason |
| --- | --- | --- | --- |
| Upload/review profile | MVC/REST | Synchronous transaction | One bounded document and draft |
| Readiness acknowledgement | MVC/REST | Synchronous transaction | One owned assessment |
| Select role intent / preview queries | MVC/REST | Synchronous transaction | At most three roles and ten markets |
| Save preferences/application/view | MVC/REST | Synchronous transaction | Small resource mutation |
| Find and rank jobs | API launch/status | Spring Batch | External failures, multiple steps, checkpoints |
| Recalculate intelligence | API/operator launch | Spring Batch | Bulk deterministic replay |
| CSV/ESCO import | Operator REST launch | Spring Batch | Paginated/chunked restartable ingestion |
| Reconcile all follow-ups | UI/API launch | Spring Batch | Bulk idempotent reconciliation |
| Generate one follow-up | Could be synchronous | Existing Batch use is educational/operational | Revisit before scale work |
| Weekly aggregation | Operator launch | Batch tasklet | Rerun/history value, not computational necessity |

An ordinary API implementation of Find Jobs would still need progress state, step history,
transactions, checkpoints, retry policy, idempotency, and restart logic. Spring Batch supplies this
execution model; it does not replace HTTP.

## 5. High-level architecture and data flow

```text
Browser / REST client
        │ workspace cookie
        ▼
Spring MVC controllers ── ownership checks ── synchronous application services
        │                                      │
        │ launch/status                        └─ profile/application PostgreSQL transactions
        ▼
Spring Batch JobRepository + JobOperator
        ▼
findJobsJob orchestration
  discovery ── provider adapters ── public APIs
      │
      ▼
raw JSONB landing → normalization → skills → duplicates → candidate scoring
      │                                                   │
      └──────────────── PostgreSQL/Flyway ────────────────┘
                              │
                              ▼
                   dashboard / REST inspection
```

Find Jobs data flow:

1. Resolve the workspace, active candidate, ordered target roles, and versioned per-market query
   profiles.
2. Fetch provider pages outside uncontrolled database transactions.
3. Persist untouched provider JSON, source URL, payload hash, and immutable run evidence.
4. Normalize new/changed raw rows in chunks.
5. derive skills, exact clusters, fuzzy suggestions, scores, and reasons deterministically.
6. Expose only workspace-sighted, active-occupation, current-market results.

## 6. Consistency, transactions, and idempotency

- PostgreSQL is the system of record and Flyway is the sole schema owner.
- Profile review and activation are separate states. Activation copies one reviewed version into the
  runnable candidate projection transactionally.
- Résumé role suggestions remain evidence; one to three user-selected target roles are stored as
  ordered intent. `role-intent-v1` query plans are persisted separately from both, so an advanced
  provider override cannot silently rewrite what role the user selected.
- `universal-v1` evaluates every posting independently against each selected role. Frontend, Backend
  Engineering, AI/ML, and Sales/Customer Success can add versioned calibrated title/skill evidence;
  an unmapped role uses the same universal dimensions without an overlay. The highest result is the
  compatibility projection, while every per-role score and reason remains candidate-private and
  reproducible. Missing signals contribute zero points and never act as hidden filters.
- A `REVIEW_REQUIRED` resume assessment must be acknowledged before activation.
- Raw identity is `(source, external_job_id)`; changed payload hashes reset derived processing.
- Chunk writers update derived state and source status in the same transaction.
- Exact duplicates are evidence-backed clusters. Fuzzy results are review suggestions, not merges.
- Job parameters identify logical Batch work; JobExecution records attempts and ExecutionContext
  stores checkpoints. Restart does not replay completed steps unnecessarily.
- Provider failures retain committed earlier results. JobLens may display `PARTIAL`, while Spring
  Batch truthfully records `FAILED` until restart succeeds.

## 7. Failure and security model

Expected failures: missing provider credentials, quota/rate limits, transient HTTP failures, malformed
provider payloads, unreadable uploads, invalid profile values, database failures, and process restart.
They must be bounded, sanitized, persisted where appropriate, and visible to the user/operator.

Security boundary today:

- An opaque browser cookie resolves workspace identity; resource IDs alone never grant ownership.
- Credentials come from environment variables and are not logged or returned.
- Original resume bytes and full extracted text are not retained.
- Workspace-private taxonomy additions are never part of another workspace's catalogue.
- This is not an authenticated multi-user boundary. Cookie theft, browser loss, cross-device recovery,
  export/deletion policy, malware scanning, and account security remain explicit future work.

## 8. Observability and scale-up triggers

Current evidence: Batch metadata, step counts, immutable per-source run summaries, sanitized failures,
Actuator health, REST run history, and dashboard partial-result reporting.

Change the architecture only when measured evidence crosses a trigger:

- Partition/parallelize discovery when normal runs exceed the five-minute target and provider quotas
  permit concurrency.
- Move fuzzy blocking into indexed SQL when its step exceeds two minutes or consumes more than 25% of
  the application heap in a representative run.
- Add query/index work when candidate-visible read p95 exceeds 500 ms at the declared 10,000-job
  target.
- Add scheduling/backpressure only after per-workspace frequency, timezone, quiet hours, and provider
  request budgets are decided.
- Add object storage only with an explicit retention, encryption, authorization, and deletion policy.
- Add login/recovery before claiming multi-user or cross-device security.

Redis, Kafka, sharding, Elasticsearch, and microservices are not current milestones. PostgreSQL and a
single deployable remain adequate until a measured trigger says otherwise.

## 9. Design risks and next decisions

1. M3.1 provides explicit role intent and reproducible provider queries. M3.2 provides universal
   role-aware ranking plus four initial overlays. Job Explorer and reviewed relevance labels remain
   M3.3–M3.4 work; the overlay seeds are transparent starting rules, not measured accuracy claims.
2. SQL resource paths are runtime strings; M2.8 will add typed startup validation without changing
   JDBC behavior.
3. Retention is indefinite and anonymous workspaces are unrecoverable.
4. Discovery is sequential and fuzzy pair enumeration is in memory.
5. The HTTP launch path and global concurrency policy need explicit load verification before
   scheduling or multi-user use.
6. Original resume storage, notifications, login, and React remain separate decisions.

## 10. Milestone mapping

- M2.7 improves requirement 1: readable documents receive transparent guidance and acknowledgement.
- M2.8 reduces persistence operability risk; it does not add a product feature.
- M3 establishes a measurable ranking-quality feedback loop for requirement 2.
- M4 decides source-document durability and privacy.
- M5 adds automation only after request budgets and delivery policy exist.
- M6 replaces the anonymous-cookie boundary with recoverable identity.

This baseline must be revised when an operating assumption, ownership boundary, external contract,
or scale trigger changes—not after every small implementation detail.
