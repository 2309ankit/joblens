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

## Recommended order

| Priority | Milestone | Why now | External dependency |
| --- | --- | --- | --- |
| 0 | Jooble live acceptance — COMPLETE | Proved the source against real Singapore responses | Regional API key configured |
| 0.5 | Portal Search Hub — COMPLETE | Open preference-filled official portal searches without scraping | None |
| 0.6 | Smart Portal Query Planner — COMPLETE | Combine roles, sectors, and resume technologies into focused searches | None |
| 1 | Source health and run observability — COMPLETE | Makes missing credentials, quota failures, source counts, and partial results clear in the UI | None for mocked tests |
| 2 | Lever public ATS source — COMPLETE | Adds legitimate employer-direct listings through a verified public API | None; direct official board URL required |
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
Preserve the current worktree. Implement only milestone M1 (or another selected milestone), including
its tests and evidence. Finish with ./mvnw clean test and do not start later milestones.
```
