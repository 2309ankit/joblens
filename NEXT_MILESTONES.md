# JobLens Next Milestone Index

This is the short decision index for future work. The startup product contract is
[PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md); [BUILD_PROGRESS.md](BUILD_PROGRESS.md) remains the
historical evidence log. Select one milestone at a time.

Every selected milestone must follow the requirement, design, verification, review, and Definition
of Done gates in [ENGINEERING_STANDARDS.md](ENGINEERING_STANDARDS.md).

## Selection rules

1. Agree on one milestone and its acceptance criteria before coding.
2. Keep the application a batch-first modular monolith.
3. Use only legitimate provider APIs or public board interfaces.
4. Preserve raw provider input before normalization.
5. Require restartability, idempotency, PostgreSQL Testcontainers, REST/UI inspection, and documented
   evidence when the milestone contains Batch work.
6. Do not bundle login, storage, scheduling, source expansion, and scoring into one change.
7. Stop after every selected module, show its evidence, and obtain explicit user approval before
   beginning the next module.

## Startup delivery order

| Priority | Milestone | Why now | External dependency |
| --- | --- | --- | --- |
| D1 | Startup requirements and architecture baseline — COMPLETE | Replaces the personal/learning boundary with users, SLOs, real-time semantics, launch gates, and a fixed/not-fixed assessment | Product assumptions require owner review |
| D2 | V1 engineering standards — COMPLETE | Defines requirement, design, implementation, verification, review, and release gates | None |
| S0 | V1 correctness stabilization — SELECTED | Reproduce and repair recorded defects in three bounded checkpoints before adding more product surface | Redacted fixtures and controlled provider access where required |
| S1 | Authenticated account ownership and RBAC | Anonymous UUID cookies cannot protect a public multi-user product | Identity-provider and account-linking decision |
| S2 | Product run commands and live progress | Add admission control, idempotency, safe concurrency/recovery, cancellation, and SSE/polling status | S1 ownership contract |
| S3 | Shared ingestion and provider budgets | Prevent equivalent user searches from multiplying external requests and cost | Provider quotas/contracts and freshness policy |
| S4 | Job Explorer and reviewed ranking quality | Add backend filters/sorts/keyset paging plus private feedback and Precision@10 | Stable run/corpus identity |
| S5 | Résumé object lifecycle and privacy workflows | Add encrypted/scanned storage, retention, export, and deletion | Storage/retention/security decisions |
| S6 | Production platform gate | CI/CD, staging/production, managed HA data, observability, backups/restore, load/security/failure testing | Deployment platform and operating ownership |

## React dashboard migration checkpoint — R1

Status: **COMPLETE on 2026-09-07; selected directly by the product owner.**

Requirement trace: `R1-REACT-DASHBOARD`. A React migration is a delivery-enabling UI change, not a
public-launch gate. It must not displace the product correctness, identity, privacy, run-control, or
operability requirements in the startup delivery order.

Actor: an active anonymous development workspace user viewing the dashboard.

Acceptance criteria:

1. `/` and `/dashboard` serve a production-built React dashboard from the same Spring Boot artifact;
   no global Node installation is required to build it.
2. The React dashboard preserves the workspace-owned dashboard read model, ranked-job actions,
   Find Jobs launch/restart controls, source diagnostics, portal link-outs, aggregate counts, and
   weekly insights.
3. The browser sends its same-origin workspace cookie. A workspace without an active profile is
   redirected to `/setup`; it never receives another workspace's data.
4. The dashboard has loading, empty, recoverable-error, and keyboard-operable action states. It
   renders product-safe error text instead of framework or Batch exception messages.
5. The existing `/setup` and `/applications` Thymeleaf flows stay available. This checkpoint does
   not change Batch, database, scoring, source, identity, or privacy behavior.
6. React unit tests, controller tests, clean Maven/PostgreSQL tests, artifact inspection, and diff
   checks pass.

Design: React is compiled by Maven with a pinned project-local Node runtime and dependency lockfile
to `target/classes/static/app`; Spring MVC forwards the established dashboard routes to that bundle.
`GET /api/dashboard` is the sole composite dashboard read model and applies the existing workspace
ownership predicate before querying jobs, applications, links, run status, or insights. Mutations
continue through existing workspace-scoped REST endpoints. Rollback is a source-only route change
back to the existing `dashboard.html`; no data migration is involved.

Explicit exclusions: migrating setup or applications, React Router, client-side paging/filtering,
authentication, changing S0.1 failure semantics, and any provider or scoring change.

Follow-up defect `BUG-R1-001` — empty outbound-search cards: **FIXED on 2026-09-07.** A legacy or
incomplete profile with neither a selected role nor a keyword override previously normalized an empty
fallback and produced blank portal-query cards. The query planner now returns no queries for that
state, the link factory returns no links, and the React portal section is omitted. Focused planner
and link-factory regression tests plus the clean full suite pass.

UI polish follow-up: pinned `lucide-react` 1.42.0 provides consistent, labelled action icons and
`sonner` 2.0.8 replaces the custom sticky status pop-down with dismissible dark-mode notifications.
These frontend-only dependencies are lockfile-backed and do not alter the dashboard API, data model,
Batch processing, source behavior, identity, or privacy scope.

Follow-up defects `BUG-R1-002` and `BUG-R1-003`: Vite now emits the published `/app/` asset base,
and server-side routing sends a workspace without a confirmed candidate profile directly to `/setup`.
The HTML entry is not cached; content-hashed assets are immutable for one year. This prevents stale
dashboard bundles without weakening the workspace ownership boundary.

## Deferred shipping milestone — S0.1 Discovery execution safety

Status: **SELECTED FOR IMPLEMENTATION; NOT STARTED**.

Requirement trace: `BUG-M3-001`, `BUG-M3-005`, `FR-04`, and the run-safety portion of `FR-10`.

Goal: make one Find Jobs command diagnosable from generated query through scored results and make a
repeated command safe and understandable. The milestone must locate and repair the first proven
failure behind the reported zero-result AI Engineer run; it must not guess that the source, query,
normalizer, or ranker is at fault.

Observed starting boundary: the page controller catches `JobExecutionException`/`RuntimeException`
and flashes `exception.getMessage()`; the REST exception handler also returns raw Batch exception
messages. `FindJobsService` calls `JobOperator.start(...)` before recording the workspace run, so an
already-running exception may occur before the current execution is projected into the product run
table. These are inspection facts, not a completed diagnosis or fix.

### S0.1 scope

- Create a redacted AI Engineer regression fixture plus a broad-role control using the same market.
- Trace selected intent and generated provider query into active source profiles and per-market run
  records.
- Reconcile requested, received, raw, normalized, sighted, and scored counts so the first zero stage
  is explicit.
- Preserve a sanitized provider request/response diagnostic without credentials or résumé data.
- Distinguish active, completed, failed, and stale Find Jobs executions at the product boundary.
- Return an existing active run or a stable product error/status when a duplicate command is made;
  never expose Spring Batch identifiers or exception text to a normal user.
- Implement only the correction supported by reproduction evidence and protect it with regression
  tests.

### S0.1 acceptance criteria

1. A deterministic test proves that selected AI Engineer intent produces the expected persisted
   query and enabled market source profile; a control role proves the same route independently.
2. Provider mock/contract tests cover a non-empty response and a legitimate empty response. Run
   counters identify the first stage at which results become zero and reconcile with persisted rows.
3. If the reported zero-result behavior is caused inside JobLens, the proven defect is fixed and a
   regression test fails without that fix. If the provider legitimately returns zero, the UI/API
   says so with market/query/source context and does not claim an internal success with missing data.
4. Two near-simultaneous commands for the same workspace/search identity create at most one active
   execution. The second request receives the existing product run state or a stable safe response.
5. A stale or failed execution is distinguishable from active work and has a documented recovery
   path. Full cancellation, queueing, leases, and SSE remain owned by S2.
6. Normal UI/API responses contain no `JobInstance`, `JobExecution`, internal job name, stack trace,
   SQL, or provider credential details. Operator inspection retains sufficient correlated evidence.
7. PostgreSQL Testcontainers covers persistence/concurrency/restart behavior; provider behavior uses
   controlled mock HTTP tests; controller tests cover safe messages; the full Maven test suite and
   `git diff --check` pass.

### S0 follow-on checkpoints — queued, not selected

| Checkpoint | Recorded items | Boundary |
| --- | --- | --- |
| S0.2 — Onboarding correctness | `BUG-M3-002`, `UX-M3-003`, `BUG-M3-004` | Role evidence deduplication/section parsing, sector control design, and optional city/region |
| S0.3 — Cross-role ranking correctness | `BUG-M3-006` | Reproduce high .NET scoring, correct the proven generic/skill/baseline defect, and preserve universal-policy behavior |

S0.1 does not include résumé suggestion changes, sector taxonomy, country/location redesign,
ranking-weight changes, Job Explorer pagination, feedback calibration, authentication, a new source,
shared ingestion, scheduling, live SSE, or service extraction.

The following table is the earlier feature-build order retained for history. Its incomplete entries
do not override the startup sequence above.

## Historical feature order

| Priority | Milestone | Why now | External dependency |
| --- | --- | --- | --- |
| 0 | Jooble live acceptance — COMPLETE | Proved the source against real Singapore responses | Regional API key configured |
| 0.5 | Portal Search Hub — COMPLETE | Open preference-filled official portal searches without scraping | None |
| 0.6 | Smart Portal Query Planner — COMPLETE | Combine roles, sectors, and resume technologies into focused searches | None |
| 1 | Source health and run observability — COMPLETE | Makes missing credentials, quota failures, source counts, and partial results clear in the UI | None for mocked tests |
| 2 | Lever public ATS source — COMPLETE | Adds legitimate employer-direct listings through a verified public API | None; direct official board URL required |
| 2.5 | Normalized multi-market preferences — COMPLETE | Fixes country/location fan-out before any frontend migration | None |
| 2.6 | Inclusive Profile Intelligence — COMPLETE | Removed the backend/IT bias and added assisted roles, skills, and countries | None |
| 2.7 | Explainable ATS Readiness Advisor — COMPLETE | Give non-blocking, evidence-based resume improvement guidance | None |
| D0 | System Design Baseline — COMPLETE | Make requirements, operating parameters, API/Batch boundaries, and scale triggers explicit | None |
| 2.8 | Typed SQL Resource Registry | Remove fragile SQL-path strings while retaining Spring JDBC | M2.7 complete and user approval |
| 3 | General Role Intent, Job Explorer, and Ranking Calibration — IN PROGRESS | Improve relevance and make country/source/ranking decisions inspectable | Reviewed job examples for calibration |
| 4 | Original resume storage | Retain the uploaded source document through a storage abstraction | Storage choice and retention policy |
| 5 | Scheduling and notifications | Automate proven manual jobs and follow-ups | Delivery channel and frequency choices |
| 6 | Login and workspace recovery | Enable cross-device use after anonymous flow is stable | Identity and privacy decisions |

## M0 — Jooble live acceptance checkpoint

Status: complete on 2026-08-31. This was an acceptance checkpoint, not a new architecture milestone.

Acceptance evidence:

- Add the key only to ignored `.env`; never commit or print it.
- Recreate the app and reconfirm workspace preferences.
- Confirm active `ADZUNA` and `JOOBLE` search profiles through SQL.
- Run **Find and rank jobs** for a fresh business date.
- Verify Jooble fetch-run status, raw JSONB rows, normalized rows, workspace sightings, scores, and
  working original-listing redirects.
- Record provider result counts and any quota/rate-limit response without exposing credentials.

Observed acceptance: Find Jobs execution 22 completed all six steps. Jooble fetched 60 records across
three pages; 60 raw JSON rows were normalized, sighted by the workspace, and scored. REST returned a
Jooble job, the dashboard rendered **Open on JOOBLE**, and the view endpoint returned `302` to the
original `sg.jooble.org` listing. The key remains only in ignored `.env`.

## M0.5 — Portal Search Hub

Status: complete on 2026-08-31.

The dashboard exposes preference-filled official searches for LinkedIn, JobStreet Singapore, SEEK
Australia, and SEEK New Zealand. They are clearly separated from **Find and rank jobs** because
outbound portal results are not copied into JobLens, normalized, deduplicated, or scored. URL creation
lives in a small tested factory rather than the controller or Thymeleaf template.

## M0.6 — Smart Portal Query Planner

Status: complete on 2026-08-31.

The query planner produces three explainable search intentions from the confirmed profile: primary
role plus sectors, alternate role plus technologies and primary sector, and a broad fallback.
LinkedIn receives quoted Boolean expressions with `AND`, `OR`, and parentheses. JobStreet and SEEK
receive shorter natural keyword phrases. Each portal link displays its intent and generated query;
three intentions across four regional portals produce twelve links without importing portal data.

## M1 — Source health and run observability

Status: complete on 2026-08-31.

Goal: show what each source did during a Find-jobs run so users understand whether results are empty,
partial, unavailable, or successful.

Proposed acceptance criteria:

- A workspace-safe run detail exposes each source profile, status, pages attempted, raw count, new or
  changed count, normalized/sighted count, and sanitized failure reason.
- Dashboard shows a compact result summary after the run.
- One failed optional source does not erase successful source results; overall status clearly reports
  partial completion according to an explicitly documented policy.
- Credentials and provider response bodies never appear in errors.
- Restart reuses completed checkpoints and retries only unfinished work.
- Flyway is used only if the existing fetch/run tables cannot represent the required evidence.
- MockWebServer and PostgreSQL Testcontainers cover success, empty response, missing credential,
  transient exhaustion, partial failure, restart, and workspace isolation.

Out of scope: adding another provider, scheduling, notifications, or changing scoring.

Implemented policy: a provider failure keeps the Spring Batch execution `FAILED` and restartable. The
workspace run view reports `PARTIAL` only when another source already completed or returned an honest
empty result. Immutable per-execution source snapshots preserve attempted/fetched pages,
received/new/changed/unchanged records, derived counts, and sanitized failures even when later runs
update the landing rows.

## M2 — Public ATS source expansion

Status: complete on 2026-08-31 for one deliberately bounded provider: Lever.

Goal: add more legitimate employer-direct listings without scraping search portals.

Candidate adapters to verify before implementation:

- Lever public postings interface.
- SmartRecruiters Posting API.
- Additional documented public ATS boards with candidate-readable job data.

Required design gate:

- Verify the current official API contract, permitted use, pagination, identifiers, rate limits, and
  job-detail URL.
- Decide how company boards enter JobLens. Prefer user-curated companies or direct official URLs; do
  not crawl Google, LinkedIn, JobStreet, or arbitrary redirects.
- Add one provider per milestone behind `JobSourceClient` and `JobPostingNormalizer`.

Acceptance criteria follow the established source contract: Flyway constraints, raw JSON landing,
provider-specific normalization, deterministic identity/hash, retries, paging checkpoints,
idempotency, source links, mocked contract tests, PostgreSQL job integration, REST inspection, and
updated runbook evidence.

Implemented slice: JobLens recognizes only direct global `https://jobs.lever.co/{site}/...` URLs,
registers the site as a workspace source, and reads its public postings API without credentials.
Provider pages land as raw JSON before Lever-specific normalization. Pagination checkpoints continue
across pages even when local keyword/location filtering yields no matches on an intermediate page.
Transient failures are bounded and restart resumes the unfinished Lever source without repeating a
completed broad source. Tracking redirects, lookalike hosts, the separate EU host, and undisclosed
board crawling remain out of scope. Any second ATS adapter requires its own contract gate and
milestone.

## M2.5 — Normalized multi-market preferences

Status: complete on 2026-08-31.

Flyway V17 replaces the single country/location columns with one `workspace_search_target` row per
market. Setup accepts up to ten explicit `CC | Location` lines. Confirmation projects one Adzuna
profile per market and only the Jooble profile matching its configured regional country. Provider
profiles, pagination, source-run evidence, and restarts remain independent. Scoring matches any
confirmed market with a specific explanation, and outbound portal links follow selected markets
instead of showing hardcoded countries. This fixes the backend model before any React work.

## M2.6 — Inclusive Profile Intelligence

Status: complete on 2026-08-31. M2.7 was subsequently approved and completed on 2026-09-01.

Goal: remove the Java/backend bias from resume onboarding while keeping extraction deterministic,
explainable, normalized, and user-controlled.

Confirmed design direction:

- Resume structure determines whether a document is readable as a resume; absence of a known skill
  must not reject a structurally valid non-IT resume.
- Expand the normalized skill taxonomy beyond backend engineering, including a strong frontend set,
  aliases, and categories that can evolve without one enormous hardcoded list.
- Extract job-title suggestions from resume headings and recent experience with evidence/confidence.
  Suggestions are never silently treated as facts.
- Replace the full skill checkbox grid with searchable detected-skill chips plus catalogue search and
  an explicit path for user additions.
- Replace comma-separated target roles with a searchable, multi-select, creatable role control.
  Detected titles appear first; the user can correct, remove, or add roles.
- Replace manual ISO country-code entry with a country-name dropdown. The application stores the ISO
  alpha-2 code internally and keeps city/region as provider-facing free text.
- Country choices must follow an explicit provider-capability catalogue; do not offer a country as an
  integrated search market when no configured source supports it without explaining the limitation.
- Preserve versioned draft-before-activation behavior, workspace isolation, normalized market rows,
  deterministic scoring, and the existing combined activation transaction.

Acceptance must include Flyway where the normalized taxonomy/role model requires it, frontend and
non-IT extraction examples, title-suggestion evidence, dropdown/addition UI behavior, REST/Swagger
inspection, PostgreSQL Testcontainers, idempotency, documentation, and `./mvnw clean test`.

Implemented slice: Flyway V18 categorizes and broadens the skill seed, adds normalized role and alias
catalogues, workspace-private user additions, and versioned skill/title suggestion evidence. V19
adds forward-only cascading cleanup for candidate/profile references to private skills.
V20 adds a restartable, idempotent ESCO release importer, active-version filtering, and persisted
uncatalogued/rejected term suggestions. Matching uses a deterministic longest-phrase automaton with
section-aware evidence, so broad terms such as `R` are not accepted as skills merely by substring.
Resume matching loads the relevant database set once and performs deterministic token-boundary
matching in memory. Structurally valid resumes no longer require a known skill. Title suggestions are
ranked by transparent evidence source and remain unselected until user review. Setup uses searchable,
creatable skill/role chips and country-name dropdowns backed by an explicit provider capability
catalogue. REST/OpenAPI exposes suggestions, searchable catalogues, and supported countries. Fresh
PostgreSQL 17 Testcontainers, template rendering, workspace isolation, idempotency, and the complete
95-test suite pass.

Out of scope: opaque AI inference, claiming a detected title with certainty, ATS-readiness scoring,
SQL data-access refactoring, React migration, or starting M2.7 without explicit user approval.

## M2.7 — Explainable ATS Readiness Advisor

Status: complete on 2026-09-01. M2.8 still requires explicit user approval.

Goal: turn resume validation into non-blocking, actionable machine-readability guidance instead of
rejecting unusual but readable resumes.

Confirmed design direction:

- Hard rejection is limited to unsupported/corrupt files, size/security failures, and no readable
  content.
- A suspicious job/interview document or unusual resume becomes `REVIEW_REQUIRED`; preserve the
  upload, show the evidence, and require acknowledgement before activation.
- Produce versioned findings for contact details, standard section headings, job titles, employment
  dates, education, parsing quality, excessive length, and format/layout risks that can be measured
  reliably for the uploaded file type.
- Findings have stable codes, severity, plain-language remediation, and limited evidence. The score
  measures machine readability, not candidate quality or employability.
- Do not claim to reproduce a proprietary ATS algorithm. Before implementation, research current
  public guidance from authoritative ATS vendors and document which observable rules JobLens uses.
- Keyword alignment requires an explicit target role or job description and must remain separate
  from generic resume readability.

Acceptance must include persisted assessment/version data, UI and REST/Swagger inspection,
deterministic unit tests, representative PDF/DOCX fixtures, workspace isolation, documentation, and
`./mvnw clean test`.

Out of scope: automatic resume rewriting, hidden employer prediction, proprietary ATS claims, LLM
dependency, M2.8 work, or starting M2.8 without explicit user approval.

Implemented slice: readable PDF/DOC/DOCX uploads receive a persisted, versioned `readability-v1`
assessment. Stable findings cover contact details, headings, title lines, employment dates,
education, extraction quality, length, document uncertainty, and DOCX risks that can be measured
from package markup. Suspicious or unusual readable documents become `REVIEW_REQUIRED` rather than
being discarded; activation requires an idempotent, workspace-owned acknowledgement. The score is
explicitly machine readability, not candidate quality, job fit, or a proprietary ATS result.
`ATS_READINESS.md` records current official Greenhouse, Workable, and SAP guidance and the exact
JobLens deductions. UI, REST/OpenAPI, generated PDF/DOCX fixtures, deterministic unit tests,
PostgreSQL persistence, and workspace isolation are covered.

## D0 — System Design Baseline

Status: complete on 2026-09-01.

`SYSTEM_DESIGN.md` defines the primary requirements, non-goals, current operating envelope,
quantified design targets, core entities, ownership model, API-versus-Batch decision table,
high-level data flow, consistency/failure model, capacity formulas, scale-up triggers, risks, and
milestone mapping. Targets that have not been load tested are labelled rather than presented as
verified production claims.

## M2.8 — Typed SQL Resource Registry

Goal: retain explicit PostgreSQL SQL and `NamedParameterJdbcTemplate` while removing fragile,
repeated resource-path strings from repositories.

Confirmed design direction:

- Do not introduce JPA merely to hide SQL; current Batch and PostgreSQL-specific access remains a
  good fit for Spring JDBC.
- Introduce module-owned typed query identifiers or registries. Repository methods reference typed
  constants rather than arbitrary `"sql/..."` strings.
- Load and validate every registered SQL resource during application startup so missing/duplicate
  mappings fail fast rather than on the first request or Batch execution.
- Preserve named parameters, external SQL files, query readability, transaction boundaries, Batch
  writers, and existing behavior.
- Evaluate jOOQ only as a documented future alternative if schema/query growth later justifies code
  generation. Do not add MyBatis, jOOQ, or a custom ORM during this bounded refactor without a new
  explicit decision.

Acceptance must include startup failure tests for missing resources, module registry tests,
repository/integration regression coverage, no inline complex SQL regression, documentation, and
`./mvnw clean test`.

Out of scope: changing persistence behavior, schema redesign unrelated to query registration, JPA,
React migration, ranking calibration, or moving to another module without explicit user approval.

## M3 — General Role Intent, Job Explorer, and Ranking Calibration

Status: historical feature program. M2.8 Typed SQL Resource Registry is deliberately deferred, not
cancelled. M3.1 completed on 2026-09-02 and M3.2 completed on 2026-09-03. The former M3.3 and M3.4
scope is now consolidated under S4 and is not the current checkpoint.

Goal: turn user-selected job-search directions into explainable provider queries, role-aware ranking,
and an inspectable job explorer. The architecture supports every catalogue or workspace-private role.
Frontend, Backend Engineering, AI/ML, and Sales/Customer Success are the first curated calibration
overlays, not a closed list of supported professions. Every role receives the deterministic universal
ranking policy; mapped roles receive additional versioned evidence from an overlay.

### Product boundary

```text
resume role evidence + user-selected target roles
  → one primary and up to two additional search directions
  → role-pack query variants per selected market
  → public provider discovery
  → deterministic role-aware scoring
  → country/source/freshness filters and reviewed feedback
  → measurable calibration
```

Role detection is advisory, not a fact claim. A strong résumé signal may suggest a direction; mixed
signals show alternatives; weak evidence does not invent a role. Suggestions and target roles are
separate facts: selecting a suggested role makes it search intent, while dismissing or ignoring it
does not alter the stored résumé evidence. Manually added roles are search intent immediately.

### In scope

- A versioned, database-owned role-pack model with role family, title aliases, core/preferred/supporting
  skill signals, and explainable scoring/query weights. All roles use their canonical title, aliases,
  confirmed candidate skills, sectors, seniority, market, work arrangement, employment type, salary,
  and freshness through the universal policy.
- Four initial curated calibration overlays: **Frontend**, **Backend Engineering**, **AI/ML**, and
  **Sales/Customer Success**.
  Existing ESCO roles remain a canonical taxonomy reference; raw ESCO labels do not automatically
  become active curated aliases.
- Resume role-direction suggestions based on title/experience/skill evidence, with selection,
  correction, ordered multi-role support, and no forced classification when evidence is weak.
- Generated provider-query preview from selected roles, confirmed skills, and selected
  country/location. It replaces the normal need to edit technical provider keywords; an advanced
  override remains optional and never changes the stored role intent.
- Deterministic, versioned scoring with role-specific title, core-skill, preferred-skill, location,
  seniority, freshness, and optional-sector reasons. No hidden scoring change.
- A workspace-safe Job Explorer: country/search-market, source, freshness, work-mode, and saved-state
  filters; Recommended and Newest sorts; country grouping; stable keyset "Load more" pagination.
- Job-level `Fit`, `Maybe`, and `Not a fit` feedback with optional reason codes. Feedback is
  workspace-private and auditable.
- A calibration view/API that reports reviewed-result quality, including Precision@10 and common
  false-positive reasons, by role pack and market.

### Acceptance criteria

1. Any catalogue or private role can be selected as intent. Frontend, Backend Engineering, AI/ML, and
   Sales/Customer Success roles receive an initial calibrated overlay; an ambiguous résumé is not
   force-classified and an unmapped role still receives the universal policy.
2. Selected roles generate bounded provider queries per selected market; the generated terms and their
   role-pack version are inspectable before discovery. Normal setup has no mandatory keyword field.
3. Every ranked job exposes the active role pack and point-by-point score reasons. A missing core skill
   lowers a score but does not silently discard a potentially relevant job.
4. The dashboard shows 20 jobs initially and supports stable Load-more pagination while preserving
   filter/sort state. Country is filter/group context, not an unexplained replacement for relevance.
5. Country choices use the existing provider-supported country catalogue. Results distinguish the
   market searched from the advertised job location when those differ.
6. Feedback, role-pack versions, recalculation, and score reasons are isolated by workspace and remain
   reproducible after restart/rerun.
7. PostgreSQL Testcontainers covers all four role overlays, the universal-only path, country/source
   filtering, cursor behavior, feedback isolation, deterministic reranking, and calibration metrics.
8. A small reviewed fixture corpus demonstrates the before/after Precision@10 outcome; production
   calibration changes require user-reviewed examples rather than guessed weight changes.

### Delivery checkpoints

1. **M3.1 — Intent and generated queries — COMPLETE:** normalize ordered target roles, preserve
   résumé evidence separately, remove mandatory provider terms, and preview reproducible generated
   queries.
2. **M3.2 — Role-aware ranking — COMPLETE:** apply one universal policy to every role, add four
   curated overlays, persist per-role score evidence, and select the best-matching target role without
   dropping jobs merely for missing a signal.
3. **M3.3 — Job Explorer — MAPPED TO S4:** move filters, stable sorting, country grouping, and 20-row
   keyset Load more to backend SQL/API while keeping lightweight browser rendering.
4. **M3.4 — Feedback and calibration — MAPPED TO S4:** add workspace-private Fit/Maybe/Not-fit
   reviews, reason codes, and sample-qualified Precision@10 reporting by role pack, market, and
   scoring version.

Each checkpoint must pass focused tests and preserve the existing one-click Batch flow before the next
checkpoint begins. Weekly market insights remain hidden with a clear empty state until their Batch job
has data; internal candidate IDs are not user-facing setup concepts.

### Explicitly out of scope

- LLM ranking, automatic self-training, or opaque role inference.
- New job providers, portal scraping, employer-board crawling, scheduling, login, or resume-object
  storage.
- Cross-workspace feedback sharing.
- Treating salary ranking across different currencies as directly comparable.

### Inputs needed after approval

For calibration evidence, collect 5–10 relevant and 5–10 irrelevant/maybe job examples for each
initial role pack. Links or copied titles/descriptions are enough; they remain local test/review
fixtures and do not require a third-party AI service.

## M4 — Original resume storage

Goal: retain original resume bytes safely without coupling onboarding to one storage vendor.

Design choices required before coding:

- Local development storage versus S3-compatible object storage.
- Maximum size, allowed types, retention/deletion, encryption, and download authorization.
- Whether extracted text is stored, regenerated, or separately encrypted.

Proposed acceptance criteria:

- A small `ResumeObjectStore` boundary owns put/get/delete operations.
- PostgreSQL stores object identity, hash, size, media type, and lifecycle state, not large file bytes.
- Upload remains validated before profile activation.
- Download and deletion enforce workspace ownership.
- Tests use a local/test adapter and prove hash integrity, replacement/versioning, unauthorized access
  rejection, and cleanup behavior.

## M5 — Scheduling and notifications

Goal: automate workflows only after their manual behavior is trusted.

Decisions required: per-workspace schedule, timezone, notification channel, quiet hours, retries, and
delivery idempotency.

Keep Spring Batch jobs explicit; a scheduler should launch them with deterministic parameters rather
than placing business logic inside scheduled methods. Persist notification attempts and outcomes.

## M6 — Login and workspace recovery

Goal: attach existing anonymous workspaces to a recoverable account without losing ownership safety.

Decisions required: identity provider or local credentials, account-linking flow, cookie migration,
data export/deletion, and privacy policy. Under the startup baseline this is no longer optional future
work; it is superseded by S1 and is required before a public multi-user launch.

## Explicitly unavailable or prohibited shortcuts

- Do not scrape LinkedIn or Indeed.
- Do not treat partner job-posting APIs as candidate search APIs.
- Do not scrape Google results to discover boards.
- Do not request or store a user's portal password to automate browser scraping.
- Do not claim an unknown end client without source evidence; future estimation must show evidence and
  uncertainty.
- Do not split the modular monolith into microservices merely to add sources.

## Starting the next session

Use this request format:

```text
Read AGENTS.md, ENGINEERING_STANDARDS.md, PRODUCT_REQUIREMENTS.md, SYSTEM_DESIGN.md,
SESSION_HANDOFF.md, README.md, BUILD_PROGRESS.md, and NEXT_MILESTONES.md. Preserve the current
worktree. Resume only S0.1; do not infer permission to implement S0.2, S0.3, or later launch gaps.
```
