# JobLens Next Milestone Index

This is the short decision index for future work. It is not a promise to implement every item. Select
one milestone at a time; [BUILD_PROGRESS.md](BUILD_PROGRESS.md) remains the historical evidence log.

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

## Recommended order

| Priority | Milestone | Why now | External dependency |
| --- | --- | --- | --- |
| 0 | Jooble live acceptance — COMPLETE | Proved the source against real Singapore responses | Regional API key configured |
| 0.5 | Portal Search Hub — COMPLETE | Open preference-filled official portal searches without scraping | None |
| 0.6 | Smart Portal Query Planner — COMPLETE | Combine roles, sectors, and resume technologies into focused searches | None |
| 1 | Source health and run observability — COMPLETE | Makes missing credentials, quota failures, source counts, and partial results clear in the UI | None for mocked tests |
| 2 | Lever public ATS source — COMPLETE | Adds legitimate employer-direct listings through a verified public API | None; direct official board URL required |
| 2.5 | Normalized multi-market preferences — COMPLETE | Fixes country/location fan-out before any frontend migration | None |
| 2.6 | Inclusive Profile Intelligence | Remove the backend/IT bias and provide assisted roles, skills, and countries | Curated starter taxonomy and user review |
| 2.7 | Explainable ATS Readiness Advisor | Give non-blocking, evidence-based resume improvement guidance | M2.6 complete and user approval |
| 2.8 | Typed SQL Resource Registry | Remove fragile SQL-path strings while retaining Spring JDBC | M2.7 complete and user approval |
| 3 | Ranking calibration workflow | Improve relevance using reviewed decisions instead of guessed weights | User-reviewed job examples |
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

Out of scope: opaque AI inference, claiming a detected title with certainty, ATS-readiness scoring,
SQL data-access refactoring, React migration, or starting M2.7 without explicit user approval.

## M2.7 — Explainable ATS Readiness Advisor

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

## M3 — Ranking calibration workflow

Goal: make ranking more useful from reviewed examples while keeping it deterministic and explainable.

Proposed acceptance criteria:

- User can mark a ranked job as relevant, neutral, or irrelevant and optionally choose reason codes.
- Feedback is workspace-owned and auditable.
- A calibration report compares current score components with reviewed outcomes.
- Weight changes create a versioned preference revision and trigger an explicit rerank.
- Existing scores remain explainable; no LLM or opaque automatic employer inference is introduced.
- Tests prove isolation, versioning, rerun idempotency, and reason reconciliation.

Out of scope: machine learning until enough reviewed examples exist.

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
data export/deletion, and privacy policy. This is a product/security milestone, not a prerequisite for
the current personal browser workflow.

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
Read AGENTS.md, SESSION_HANDOFF.md, README.md, BUILD_PROGRESS.md, and NEXT_MILESTONES.md.
Preserve the current worktree. Start only M2.6 Inclusive Profile Intelligence. Before coding, inspect
the current skill seed, resume parser, profile schema, setup UI, scoring, and provider country support.
Implement M2.6 with Flyway where needed, deterministic extraction, user-reviewable UI, REST/Swagger,
PostgreSQL Testcontainers, idempotency, and documentation. Finish with ./mvnw clean test and a
conventional commit. Then stop, show evidence, and ask me before starting M2.7. Do not start M2.7,
M2.8, React migration, ranking calibration, or unrelated work.
```
