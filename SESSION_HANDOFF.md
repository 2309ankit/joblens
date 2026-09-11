# JobLens Session Handoff

Use this file to resume work quickly. [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) is the
authoritative startup product/launch contract, [SYSTEM_DESIGN.md](SYSTEM_DESIGN.md) is the target
architecture, [BUILD_PROGRESS.md](BUILD_PROGRESS.md) is historical evidence, [README.md](README.md)
is the local operator guide, [ENGINEERING_STANDARDS.md](ENGINEERING_STANDARDS.md) is the mandatory
development/review contract, and [NEXT_MILESTONES.md](NEXT_MILESTONES.md) indexes selectable work.

## 1. Resume checkpoint

```text
Repository: /Users/ankitkumar/IdeaProjects/joblens
Branch: main
Implementation baseline: M3.2 Role-Aware Ranking + S0.1 Discovery Safety + assisted multi-market onboarding
M3.2 implementation commit: 2885c84 (feat(ranking): add role-aware calibration)
Latest onboarding-fix commit: 482a3b5 (fix(onboarding): support assisted multi-market setup)
Product baseline: D1 Startup Requirements and Architecture
Engineering baseline: D2 JobLens V1 Engineering Standards
Product/release/artifact: JobLens / V1 / 1.0.0-SNAPSHOT
React surface checkpoint: R1.1 — COMPLETE (2026-09-08)
Assisted multi-market onboarding follow-up — COMPLETE (2026-09-08)
Next shipping milestone: D3 Free Demo Deployment — COMPLETE (2026-09-10), including post-deploy
incident fixes on 2026-09-10 and a second post-deploy fix round on 2026-09-11 (see below).
S0.4 Semantic Skill Extraction — IMPLEMENTED 2026-09-11, shipped disabled by default (see below and
[S0_4_SEMANTIC_SKILL_EXTRACTION.md](S0_4_SEMANTIC_SKILL_EXTRACTION.md)); await owner direction for the
next module.
Java: 21
Spring Boot: 4.1.1 (deliberate recorded deviation from the original 3.x request)
Spring Batch: 6
Database: PostgreSQL 17
Latest Flyway migration: V29 (add_semantic_skill_match_evidence — additive `matched_canonical_term`
column)
Latest implementation commit: 6e01af2 (feat(onboarding): add semantic skill matching, shipped disabled by default)
  — committed to `main` but **not pushed** this session; `git log origin/main..HEAD` will show it ahead
  of the remote.
Latest full test: 155 Java tests, 0 failures, 0 errors, 0 skipped (React suite unchanged at 12 tests;
not touched this session)
Local runtime: rebuilt and verified this session on the S0.4 code — `docker compose up -d
--force-recreate app` starts cleanly in 2.01s, `/actuator/health` UP, idle memory 301 MiB (`docker
stats`, no artificial limit), image 341,930,845 bytes (~326 MB, up from the D3-era ~160 MB — see S0_4
doc §8 for why). Semantic matching is OFF at runtime
(`joblens.onboarding.semantic-matching.enabled=false` default).
Render runtime: joblens-demo (srv-dahcds6q1p3s73ec8i5g) deploy dep-dahf5irl550s7381j210 of commit 8170d61 is live (unchanged this session — commit 6e01af2 not pushed/deployed); /actuator/health reports UP.
Auto-deploy is now ON (`autoDeployTrigger: "commit"` in render.yaml and on the live service) — a push
to `main` deploys automatically; `render deploys create` is no longer required for routine pushes. A
push of 6e01af2 would auto-deploy S0.4 (disabled by default, so this is low-risk) to the live demo.
Owner-reported next items: D3 (including both post-deploy incident-fix rounds) is complete; S0.4 is
implemented, committed, and shipped disabled, but not yet pushed; S0.2 is paused and acceptance-open;
the S0.3 recommendation diagnosis is recorded below, but its implementation remains queued. Next
session should confirm whether to push 6e01af2, then ask the owner which module to pick up next (S0.2
acceptance, S0.3, enabling S0.4 in production via a base-image change, or another).
```

Before making changes:

```bash
cd /Users/ankitkumar/IdeaProjects/joblens
git status --short
docker compose ps
```

Read [AGENTS.md](AGENTS.md), [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md),
[SYSTEM_DESIGN.md](SYSTEM_DESIGN.md), and [ENGINEERING_STANDARDS.md](ENGINEERING_STANDARDS.md), then select exactly one milestone from
[NEXT_MILESTONES.md](NEXT_MILESTONES.md). Do not infer or combine milestones.

## D3 Free Demo Deployment — COMPLETE (2026-09-10)

The owner selected D3 on 2026-09-10 and paused further product work until the demo is deployed. Its
bounded requirement and design record is
[D3_FREE_DEMO_DEPLOYMENT.md](D3_FREE_DEMO_DEPLOYMENT.md). The repository defines one Render Free
Singapore Docker web service backed by an owner-created Neon Free PostgreSQL 17 Singapore database;
the environment is limited to synthetic or redacted data and does not satisfy S6 or private-beta
launch gates.

The clean suite passed with 138 Java and 12 React tests. The 167,668,389-byte runtime image is
non-root and contains no build toolchain. At 512 MB and 0.1 CPU it reached `UP` in 193 seconds, used
approximately 240 MB at idle, served `/setup` and hashed assets, and emitted the required secure
cookie. Formatting and diff checks passed.

Remote evidence recorded 2026-09-10: service `joblens-demo` (`srv-dahcds6q1p3s73ec8i5g`) deploy
`dep-dahcdsuq1p3s73ec8kv0` of commit `c1fd772` reached `live`. Remote Flyway logs show all 26
migrations validated and applied against the fresh Neon Singapore database. The public URL
(`https://joblens-demo.onrender.com`) returned HTTP 200 `UP` from `/actuator/health`, served `/setup`
with both content-hashed assets, issued the workspace cookie with `Secure; HttpOnly; SameSite=Lax`,
and `/api/source-boards` returned HTTP 200, confirming live database connectivity. No error-level
application logs were observed. Full evidence is in `BUILD_PROGRESS.md`. D3 is complete; await
explicit owner direction before selecting the next module (S0.2 acceptance, S0.3, or another
milestone).

## D3 post-deployment incident fixes — COMPLETE (2026-09-10)

Two issues surfaced immediately after the owner started using the live demo. Both are fixed, tested,
committed to `main`, and redeployed; this is bug-fix follow-through on the D3 environment, not a new
module, and does not authorize starting S0.2/S0.3/S1–S6 without a separate owner checkpoint.

**Missing provider credentials.** The Render service was created without `ADZUNA_APP_ID`,
`ADZUNA_APP_KEY`, or `JOOBLE_API_KEY` — `render.yaml` never declared them and the original creation
script omitted them, so Find Jobs failed immediately with `MissingJobSourceCredentialsException`.
Fixed by setting the three variables on the Render service via the Render API. A plain service
`restart` did **not** pick up the new values (the same error persisted across two restarts on the
existing container); a full `render deploys create` of the already-live commit was required to
recreate the container with fresh environment injection. Verified via `/actuator/health` and clean
application logs after the redeploy.

**Adzuna/Jooble zero-result queries.** `ProviderQueryPlanner` builds each query as a role name plus up
to two skills, AND-joined into one search string (see `S0_2`-era `role-intent-v1` generation). A
narrow combination — e.g. `"Backend Engineer Apache Camel IBM MQ"` — can legitimately return zero live
postings even with correct setup and credentials. This was confirmed to be identical behavior in the
local Testcontainers-backed database (same narrow queries returned `EMPTY` locally too), ruling out a
Render-specific defect before any code changed. Fixed in commit `45391a6`
(`fix(discovery): broaden provider queries that return zero results`): `JobDiscoveryTasklet` now
retries a fresh (page-1) empty search with progressively fewer trailing terms — up to two extra
attempts — before accepting the empty result. Scoped to providers where broadening is safe via a new
`JobSourceClient.supportsQueryBroadening()` default method (`true` for Adzuna and Jooble; Greenhouse
and Lever are unaffected since they already OR-match keyword tokens locally against a fully fetched
board, so provider-side AND-narrowing does not apply to them). The originally persisted
`search_profile.keywords` and the reporting-layer `workspace_search_source_run.query_text` are left
untouched by broadening — only the outgoing provider request is widened, so the UI still shows the
optimal intended query.

Also found but **not fixed**: one workspace-private `skill` row named literally `AWS (S3` (unclosed
parenthesis, `USER_DEFINED` category) in the local database, most likely bad manual test data entered
during earlier onboarding testing. It degrades that one generated query's readability but does not
block discovery (broadening still finds results around it). Left for a future onboarding-data-quality
pass; do not silently delete it without first checking whether it is expected fixture/test data.

Verification: full suite passed at 145 Java tests (138 baseline + 4 new `QueryBroadeningTests` + 3 new
`JobDiscoveryIntegrationTests` broadening cases — `broadensNarrowQueryWhenFirstAttemptReturnsNoResults`,
`staysEmptyWhenEveryBroadenedAttemptAlsoReturnsNoResults`,
`doesNotBroadenAnEmptyLaterPageThatEndsNormalPagination`) plus 12 React tests, 0 failures, 0 errors, 0
skipped; `spotless:check` and `git diff --check` passed. Fixing this also required updating
`FindJobsIntegrationTests.reportsACompletedSourceWithNoResultsAsEmpty` to enqueue the one additional
broadened-empty mock response the new retry now makes — its shared static `MockWebServer` queue is not
per-test-isolated, so one missed enqueue there previously cascaded into five unrelated test failures
and one timeout across the rest of that file; if a future change to broadening attempt counts breaks
that file again, check every `ADZUNA.enqueue(...)` call's response count first, in file order, before
assuming individual tests are wrong. The local Docker image was rebuilt on this commit and Find Jobs
was re-run against a live local workspace with real Adzuna credentials: three previously-`EMPTY`
queries (`"AI Engineer JWT Node.js"`, `"Senior AI Engineer AI AWS (S3"`,
`"senior software engineer AI AWS (S3"`) all broadened successfully and returned 17–20 real records
each. Pushed to `origin/main` and redeployed to Render: deploy `dep-dahdeklg1s2s73c5n2h0` of commit
`45391a6` is `live`; remote `/actuator/health` returns `UP` with no error-level logs since the
redeploy (only benign SpringDoc and PDF-font-fallback warnings).

Current live Render state, for the next session:

- `ADZUNA_APP_ID`, `ADZUNA_APP_KEY`, and `JOOBLE_API_KEY` are now set on `joblens-demo`. Values are not
  recorded here; check the Render dashboard environment tab if rotation is ever needed.
- Deployed commit: `45391a6`. Auto-deploy remains off (`autoDeployTrigger: "off"` in `render.yaml`) —
  any further code change needs an explicit `render deploys create` (or a dashboard deploy) after
  pushing to `main`. A plain `restart` is confirmed **insufficient** to apply new environment
  variables; use a full redeploy for that.
- The Render CLI v2.22.0 binary used this session lives at
  `/private/tmp/joblens-render-cli-v2.22.0/clean/cli_v2.22.0` and is already authenticated
  (`~/.render/config.yaml`, workspace `tea-dahc7eqd0e5s738rdge0`). It is **not** on `PATH`, and that
  `/private/tmp` path will not survive a reboot — re-download and re-authenticate
  (`render login`) if it is gone.

## D3 post-deployment fixes, round 2 — COMPLETE (2026-09-11)

Bug-fix/infra follow-through on the live D3 demo, prompted by owner-reported issues while using it.
Not a new module; does not authorize starting S0.2/S0.3/S1–S6 without a separate owner checkpoint.

**Duplicate profile-version rows on résumé re-upload.** `OnboardingService.upload()` called
`OnboardingRepository.createDraft()` unconditionally on every call, and `create-draft.sql` had no
idempotency guard, so a double submit/resubmission of the same résumé inserted a second full
`workspace_profile_version` DRAFT row (each with its own skills, suggestions, and readiness
assessment) instead of reusing the existing one. Fixed in commit `0612903`
(`fix(onboarding): stop resume re-upload from duplicating draft profiles`): `create-draft.sql` now
upserts on a new partial unique index `workspace_profile_one_draft_per_resume_idx`
(`workspace_id, resume_id` where `status='DRAFT'`, migration `V27`), which also deletes any
duplicates the bug had already created. Regression test:
`WorkspaceOnboardingIntegrationTests.resubmittingTheSameResumeReusesTheExistingDraftInsteadOfDuplicatingIt`.

**Auto-deploy enabled.** The owner asked why deploys were manual and to turn auto-deploy on. Commit
`c7ca62e` flips `render.yaml`'s `autoDeployTrigger` from `"off"` to `"commit"`; the live service was
also updated directly via the Render CLI (`render services update ... --auto-deploy`). This reverses
D3's original "controlled manual promotion" design choice (see `D3_FREE_DEMO_DEPLOYMENT.md`
acceptance criterion 2 and its stated rationale — no S6 CI/CD gate yet); that document is now stale on
this point and should be reconciled if a future session has time. Practical effect: any push to `main`
now deploys to `joblens-demo` automatically, with no promotion gate beyond CI/tests run locally before
pushing.

**Keep-alive workflow added.** Commit `16a008f` adds `.github/workflows/keep-render-warm.yml`, a
GitHub Actions cron (`*/10 * * * *`) that pings `/actuator/health` to keep the free Render instance
from idling to sleep, chosen over a Render-side cron job because D3's scope explicitly excludes Render
background workers/cron jobs.

**Résumé skill-extraction recall gap.** The owner uploaded a senior sales/account-management résumé
(redacted evidence: Sahil Singh — Calsoft/Tata Elxsi/Envision/Tech Mahindra) and only `Oracle` and
`CRM` were detected as skills, despite the résumé explicitly listing Salesforce, Zoho CRM, ZoomInfo,
LinkedIn Sales Navigator, BANT/MEDDPICC, upselling, cross-selling, account mining, proposal
management (RFXs), etc. Root cause: `ProfileIntelligenceExtractor` (`esco-deterministic-v4`) is an
exact-phrase Aho-Corasick matcher (`PhraseAutomaton`) against the literal `skill`/`skill_alias` catalog
rows — not fuzzy or semantic — and the global catalog (`V5`, `V18`) was seeded almost entirely around
one Java/backend-engineer profile plus a handful of one-word business terms; it had no sales-tool
brand names, methodology acronyms, or process terms at all. This matches the open item already
recorded in [11. Known limitations](#11-known-limitations).

Commit `8170d61` (`feat(onboarding): expand skill catalog with sales/business-development terms`)
adds migration `V28`, seeding ~34 sales/business-development skills and aliases (Salesforce, Zoho CRM,
Microsoft Dynamics 365, HubSpot, ZoomInfo, LinkedIn Sales Navigator, Outbound Prospecting, Cold Calling,
Cold Email Outreach, Lead Generation/Qualification, BANT, MEDDIC/MEDDPICC, consultative/solution
selling, account mining/farming/management, upselling, cross-selling, customer retention, pipeline
management, sales forecasting, territory management, enterprise/inside sales, sales enablement, quota
attainment, negotiation, proposal management/RFX/RFP, executive presentations, Microsoft Office Suite,
client onboarding, product adoption) — including `Account Management`, which prior fixtures/tests
referenced as an expected catalog skill but which no migration had actually seeded. The same commit
fixes a latent bug this exposed: `insert-esco-skill.sql`/`insert-esco-role.sql`'s `ON CONFLICT` upsert
never stamped `taxonomy_source = 'ESCO'` when updating a pre-existing catalog row (only
`taxonomy_version`/`external_uri`), so a hand-seeded row later "claimed" by a real ESCO import kept
looking non-ESCO-sourced; `EscoTaxonomyImportIntegrationTests` caught this once `V28` pre-seeded
`Account Management`, which collided by name with that test's ESCO fixture. Regression test:
`WorkspaceOnboardingIntegrationTests.extractsSalesToolsAndMethodologiesFromTheExpandedCatalog`.

This is a **catalog-content fix, not an algorithm change** — the matcher is still exact-phrase, so
recall on any résumé domain/vendor term not literally in the (now larger, but still hand-curated)
catalog will remain zero. The owner and this session discussed replacing/augmenting the exact matcher
with local-embedding cosine-similarity matching (no external API — see the candidate checkpoint in
[NEXT_MILESTONES.md](NEXT_MILESTONES.md#semantic-skill-extraction-candidate-not-yet-selected)) as the
actual fix for open-ended recall, but explicitly asked to record it as a queued milestone rather than
implement it in this session.

Verification: full Java suite (147 tests, 0 failures/errors/skipped) and `fmt-maven-plugin:check`
(formatting) passed locally after each change. React suite not touched this session. Pushed to
`origin/main`; Render auto-deploy (see above) plus one manual `render deploys create` picked up the
final commit — deploy `dep-dahf5irl550s7381j210` of commit `8170d61` is `live`, `/actuator/health`
returns `UP`. The Neon database's fresh Flyway history was not independently re-verified remotely this
session beyond the successful deploy (health check implies migrations applied; no separate `psql`
inspection was run against Neon).

## S0.4 Semantic Skill Extraction — IMPLEMENTED, shipped disabled by default (2026-09-11)

The owner selected S0.4 (the candidate queued in the previous section) this session. Per
`NEXT_MILESTONES.md` Selection rule 1, a full design record was written and agreed
([S0_4_SEMANTIC_SKILL_EXTRACTION.md](S0_4_SEMANTIC_SKILL_EXTRACTION.md)) before any code — four design
trade-offs (ONNX runtime library, catalog-embedding cache strategy, auto-accept risk tolerance, 512 MB
fallback plan) were each explicitly decided with the owner before implementation began. Committed as
`6e01af2`; **not pushed** — see the resume checkpoint above.

**What it does.** `ProfileIntelligenceExtractor`'s existing exact-phrase `PhraseAutomaton` pass is
unchanged and still runs first. A new second pass embeds unmatched explicit-skills-list candidate
phrases (e.g. "Cold Email Outreach") and unmatched catalog skills using a local, bundled
`all-MiniLM-L6-v2` sentence-transformer (quantized ONNX, 23 MB, via DJL + ONNX Runtime, CPU-only, no
external API/network call at request time) and scores cosine similarity. A match above
`auto-accept-threshold` (provisional 0.80) becomes a confirmed skill (`matchType="SEMANTIC"`); a match
above `suggest-threshold` (provisional 0.55) becomes a reviewable suggestion carrying the matched
canonical skill name; below that, behavior is identical to before. `EXTRACTOR_VERSION` bumped
`esco-deterministic-v4` → `esco-semantic-v5`. New migration `V29` adds `matched_canonical_term` to
`workspace_profile_term_suggestion`. Implemented for skills only (the originating recall gap), not
roles. Full design/scope detail, including three real bugs found only by running the actual bundled
model (DJL's `model.onnx` filename requirement, a required `token_type_ids` input, and a
`commons-compress` version conflict with Tika that broke PDF parsing), is in the design doc §8.

**Why it ships disabled.** Building and running the real deployment Docker image
(`eclipse-temurin:21-jre-alpine`, same as D3) — not reachable by `mvn test`, which runs on macOS —
crash-looped with `UnsatisfiedLinkError: libstdc++.so.6: No such file or directory`: ONNX Runtime's
published native library is glibc-linked and Alpine uses musl. Adding `apk add gcompat libstdc++` (the
standard documented Alpine glibc-compat workaround) was tried and **confirmed insufficient** — it
progressed to a different, deeper failure, `Error relocating libonnxruntime.so: __sprintf_chk: symbol
not found` (a glibc `_FORTIFY_SOURCE`-hardened symbol `gcompat` doesn't implement). The owner decided,
rather than switch the runtime base image unilaterally, to ship with
`joblens.onboarding.semantic-matching.enabled=false` as the default (env override
`JOBLENS_SEMANTIC_MATCHING_ENABLED`) and defer the base-image decision. The `gcompat`/`libstdc++`
Dockerfile line was added then reverted (confirmed not to fix the problem; `git diff Dockerfile` is
clean). Verified with the disabled default: real Docker image rebuilt and started cleanly, `Started
JoblensApplication in 2.01 seconds`, `/actuator/health` UP, 301 MiB idle memory, zero ONNX-related log
lines.

**Known follow-up, not done this session** (see design doc §8 "What flipping this on in production
requires"): a base-image decision (e.g. `eclipse-temurin:21-jre-noble`) is the only confirmed-working
fix for Alpine/musl; the full §4 fixture-sweep threshold calibration remains outstanding (only one real
data point exists — "Zoho CRM" vs. "managed pipeline in Zoho" scored ~0.50, below the provisional 0.55
suggest-threshold); and the packaged jar/image grew from the D3-era ~160 MB to ~326 MB even with the
feature disabled, since the toggle skips loading the model but not bundling the DJL/ONNX Runtime
dependencies (the default `onnxruntime` Maven artifact bundles native libraries for every OS/arch, and
no Linux-x64-only alternative is published) — this is new capacity-planning evidence for whoever revisits
enabling it in production, not covered by the original "~90 MB model" budget sketch.

Verification: full Java suite (155 tests — 147 baseline + 8 new: 4
`ProfileIntelligenceExtractorSemanticMatchingTests`, 4 `OnnxTextEmbeddingModelTests` — 0
failures/errors/skipped) and Spotless/`git diff --check` passed locally, both before and after the
disabled-by-default change. React suite not touched this session. Committed as `6e01af2`; not pushed —
Render is unaffected.

#### BUG-ONBOARDING-06 — "Read selected resume" can silently no-op right after choosing a file

Status: **OBSERVED 2026-09-11, not confirmed against a real user click, not fixed.** While
investigating an owner report that a résumé upload "wasn't reading," direct backend testing (`curl`
against `/api/candidate-profile/resume`, and the owner's own eventual successful browser attempt) proved
`OnboardingService.upload()`/`ProfileIntelligenceExtractor` correctly parse and extract the résumé every
time — this is not a backend regression from S0.4. But one Chrome browser-automation reproduction showed
the "Read selected resume" button click producing **zero** network request and the page silently
continuing to show the previously-loaded profile's stale evidence, with no error and no loading state
change. `Setup.jsx`'s `upload(file)` returns immediately with no feedback if `file` is falsy
(`if (!file) return;`), so if the file-input's `change` handler hasn't finished updating React's `file`
state by the time the button is clicked, the click is a silent no-op indistinguishable from success. Not
yet confirmed whether a real (non-automated) fast click can trigger this, or whether it is specific to
programmatic file-input events; needs a deliberate repro (rapid select-then-click) before scoping a fix.
Separately, the owner confirmed the specific "wrong skills showing" incident was this session's own
testing artifact (see below), not this bug.

#### UX-DASHBOARD-02 — React dashboard drops the per-session "viewed" indicator

Status: **CONFIRMED 2026-09-11, not fixed.** The pre-React `dashboard.html` Thymeleaf template (now
dead code — `DashboardController` only forwards to the React bundle, nothing returns the `"dashboard"`
view name anymore) rendered `Open on {source} · viewed {viewCount}` once a job had been opened.
`sql/dashboard/list-ranked-jobs.sql` still computes `COALESCE(v.view_count, 0) AS view_count` and
`DashboardApiController`'s `/api/dashboard` response passes every SQL column straight through as a raw
`Map<String,Object>` (see `DashboardApiController.row()`), so `view_count` **is already present** in the
JSON payload React receives — this is a pure frontend gap, not missing data. `frontend/src/main.jsx`'s
`JobCard` and `FeaturedJob` components never read or render it, so a job opened earlier in the session
shows no visual difference on the dashboard. Fix is contained to `main.jsx`: read `job.view_count` and
render an indicator, mirroring the old Thymeleaf copy. Not started this session — the owner asked for it
to be logged, not fixed, while priority went to verifying the live Render demo.

#### Workspace-per-browser confusion (not a bug, but worth recording)

The owner's earlier "resume reading isn't working" / "wrong skills showing" reports on 2026-09-11 turned
out to have two real causes, both now resolved and confirmed **not** product defects:

1. This session's own browser-automation testing (`claude-in-chrome`) shared a Chrome cookie jar with
   the owner's own long-running local dev workspace (`755772bc-b5a4-4f1b-8d9d-2d1396572437`, history back
   to 2026-09-01). A synthetic "Backend Engineer" test résumé uploaded during S0.4 Docker verification
   created a new draft (`workspace_profile_version.id=65`, version 57) that became the workspace's
   "latest" (selection is `ORDER BY version DESC`, any status — see
   `sql/onboarding/find-latest-profile.sql`), so the owner's own `/setup` page started showing that
   unrelated test data instead of their real profile. Deleted with the owner's explicit approval
   (`workspace_profile_version.id=65`, `workspace_resume.id=48`); the workspace's real `ACTIVE` profile
   (version 56) is restored as latest.
2. Anonymous workspace identity is a browser cookie (`WorkspaceContext`, `JOBLENS_WORKSPACE`), so
   **different browsers get different, disconnected workspaces by design** — the owner confirmed testing
   in both Safari and Chrome, which explains results looking inconsistent between them. This is expected
   anonymous-cookie behavior (`S1` authenticated ownership is the eventual fix for cross-device
   continuity), not a defect. Each of this session's own repro attempts (`curl`, and each fresh
   `claude-in-chrome` tab) also created its own brand-new workspace for the same reason — evidence should
   be read per-workspace-id, not assumed to accumulate in one place, when debugging future local-dev
   reports.

## City suggestions scoped to country — COMPLETE, deployed and verified live (2026-09-11)

Owner request: the "City or region" field on the setup page's search-markets editor should
suggest cities based on the row's selected country. That field was plain free text with zero
suggestions — no city/geography data existed anywhere in the codebase.

Added a bundled reference dataset rather than a live geocoding API (no per-keystroke external
cost/dependency): a new `city` table (migration `V31__create_city_catalog.sql`) seeded with
17,275 cities (population ≥ 15,000, deduplicated) across the 21 currently-integrated countries,
filtered from GeoNames' public-domain `cities15000` dataset (CC BY 4.0, attributed in
`README.md`). New `GET /api/candidate-profile/cities/catalog?countryCode=XX&query=YY` endpoint
(`CityOption`, `ProfileIntelligenceRepository.cityOptions`, `OnboardingService.cityOptions`,
mirroring the existing sector-catalog pattern but without workspace scoping, since geography data
isn't workspace-private). Frontend: extracted `MarketRow` out of `SearchMarketsEditor` in
`Setup.jsx` so each market row can hold its own debounced suggestion popup, reusing the already-
tested `catalogPopupOpen` helper; free-text entry is completely unaffected — selecting a
suggestion just fills the same field a keystroke would.

177 Java tests (173 baseline + 4 new: prefix search scoped correctly, no cross-country leakage,
population-descending ordering, malformed-country-code rejection) and 21 frontend tests pass;
migration applies in ~230ms including the bulk seed; Spotless and `git diff --check` pass. Manually
verified end-to-end locally via `docker compose` and the browser: typing "guada" with Mexico
selected correctly suggested Guadalajara/Guadalupe/Guadalupe Victoria, selection filled the field,
and switching countries re-scopes the list — tested carefully against the owner's real local dev
workspace without touching their saved search markets (added a throwaway row, removed it before
leaving, never clicked Activate). Deployed as commit `01d3918`; live verification (Malaysia/"kuala"
→ Kuala Lumpur and 9 others) confirmed the backend worked correctly.

**Follow-up fix, same day**: the owner reported "I don't see it" on the live demo. Root cause was
UX clarity, not a functional bug — the popup only appeared after typing 2+ characters, and when
the exact full city name was typed (e.g. "Kuala Lumpur"), the single returned suggestion just
echoed the text already in the field with no visual distinction, making it look like nothing had
happened. Verified this precisely by reproducing "Malaysia + Kuala Lumpur" live and inspecting the
DOM directly (the popup *was* rendering — a real `listbox` with the correct option — just visually
indistinguishable). The owner then asked for a scrollable browse-first UX instead: click the field
→ immediately see a scrollable list of that country's top cities (no typing required) → narrows as
you type. Backend already supported this (blank query already returns top-population cities, same
code path used for filtered search — no backend change needed). Frontend-only follow-up:
`MarketRow` now tracks focus and fetches on focus regardless of query length (immediate, no
debounce, when query is empty; existing 250ms debounce only applies once typing starts); each
suggestion row now shows a `MapPin` icon for visual distinction; added `onMouseDown`
`preventDefault()` on the results container so clicking a suggestion doesn't blur-and-close the
popup before the click registers (the classic combobox race condition); city field is disabled
with a "Choose a country to browse its cities" hint until a country is picked. Manually verified
locally again (Singapore: click → immediate single-item list with pin icon, click to select, focus
preserved; Mexico: click → scrollable 8+ city list with a visible scroll cutoff, confirming the
"like a scroll" behavior the owner asked for) — same careful non-destructive testing against the
owner's real local workspace. 21 frontend tests still pass; no backend changes so the Java suite is
unaffected by this round.

**Committed locally, not pushed** — pending owner review before push, consistent with this
session's practice for changes touching production onboarding flow.

## Jooble multi-country architecture — COMPLETE, deployed and verified live (2026-09-11)

Product 1 of a roadmap the owner requested: expand job discovery to Malaysia and India, and
(later, separately) Naukri and foundit. Research this session established: India already works
via Adzuna (one key covers many countries); Malaysia has no Adzuna coverage at all; Jooble likely
covers Malaysia but issues a **separate API key per country subdomain**
(`sg.jooble.org`/`my.jooble.org`/`in.jooble.org` each need their own key), and the existing
`JoobleProperties`/`JoobleJobSourceClient` only supported one Jooble country/key pair at a time —
a real architecture gap, not a config tweak. Seek requires SEEK partner approval and is built for
employers posting jobs, not for pulling search results into an aggregator — not a fit regardless
of architecture. Naukri/foundit have no public API; the owner chose to build an in-house scraper
for those later (accepting the ToS/maintenance risk directly) rather than pay a third-party
scraper service (~$0.0004–0.001/job on Apify).

This change generalizes Jooble to support any number of country/key pairs:
`JoobleProperties.countries` is now a `List<JoobleCountryCredential>` (was singular
`apiKey`/`baseUrl`/`countryCode`); `JoobleJobSourceClient` resolves the right country's base URL
and key per request from `SearchProfile.sourceKey()` instead of one WebClient baked to one
country at construction; `ProviderCountryCatalog` expands the integrated-country list from
`joobleProperties.configuredCountryCodes()` instead of a single hardcoded code.

**Backward compatible with the live Singapore integration on purpose**: Singapore keeps reading
the exact same `JOOBLE_API_KEY`/`JOOBLE_BASE_URL` env vars already set on the live Render service
— zero Render changes needed for Singapore to keep working. Malaysia and India are wired with new,
distinctly-named env vars (`JOOBLE_MY_API_KEY`/`JOOBLE_MY_BASE_URL`,
`JOOBLE_IN_API_KEY`/`JOOBLE_IN_BASE_URL`, defaulting to `my.jooble.org`/`in.jooble.org`) that are
simply inactive — same fail-safe pattern as today — until the owner registers for those countries'
Jooble keys and sets them on the live service (same out-of-band pattern as the existing three
credential vars; not declared in `render.yaml`, same as today).

173 Java tests (164 baseline + 9 new: `JooblePropertiesTests` covering multi-country
binding/validation, `JoobleJobSourceClientTests` covering per-country routing to independent mock
servers and country-specific missing-credential messages, `ProviderCountryCatalogTests` covering a
second Jooble-only country appearing correctly) and 21 frontend tests, 0 failures; Spotless and
`git diff --check` pass. No other code constructs `JoobleProperties` directly (confirmed via
repo-wide search), so the blast radius is contained to the files above plus the two existing test
call sites that needed updating for the new constructor shape.

**Deployed and verified live** (commit `96b64cc`, redeployed as `122fd5d`'s successor via
`96b64cc`): the owner obtained both keys same-day and set them directly on the live Render service
(`JOOBLE_MY_API_KEY`, `JOOBLE_IN_API_KEY`) via the Render API. First deploy attempt hit a transient
Neon "terminating connection due to administrator command" during Flyway init (unrelated to this
change — no migration in this commit — Render correctly kept serving the prior live deploy without
disruption); the retry succeeded cleanly. Live `/api/candidate-profile/countries` confirmed:
`MY` → `["JOOBLE"]` (new), `IN` → `["ADZUNA","JOOBLE"]` (was Adzuna-only), `SG` unchanged.

**Next steps** (separate checkpoints, not something this session can do without the owner):
a from-scratch Naukri scraper, then a from-scratch foundit scraper (both accepted-risk, in-house,
no third-party scraper service, per the owner's explicit choice this session).

## S0.3 Cross-role ranking correctness — COMPLETE (2026-09-11)

The owner selected S0.3 on 2026-09-11. Fixes BUG-M3-006 (a `.NET Engineer` posting scoring highly
for Java Backend/Frontend candidates) and UX-DASHBOARD-01 (no relevance boundary between "Featured"
and everything else).

**Root cause**, confirmed by reading `JobScoreCalculator.java` directly: `titleScore()` exact-phrase
matches a job title against the candidate's raw target-role name for 25/25 points with no generic-word
filtering (filtering only applied in the token-overlap fallback). A candidate whose only target role
is the broad phrase "Software Engineer" gets a `.NET Software Engineer` posting the full 25 title
points; combined with baseline points awarded even absent real fit (unspecified seniority → 5, market
match → 5, employment `ANY` → 5, missing salary → 3 by default), a job with zero skill/sector/freshness
evidence still totals 43 and gets featured. A pure title-regex fix was rejected: filler words like
"software"/"backend" survive any generic-word stoplist and remain too broad to be meaningful
(`.NET Software Engineer` and `Java Software Engineer` are lexically identical on "software" alone).

**Fix**: added a new, separately-computed, versioned `qualifiesRecommended` boolean
(`job_score`/`job_role_score.qualifies_recommended`, migration `V30`) alongside the existing
(unchanged) additive scoring dimensions — `POLICY_VERSION` bumped to `universal-v2`. A job qualifies
only when it has real skill evidence (`skill > 0`, confirmed or calibrated) or a *curated* title match
(a role alias, a calibration-pack title signal, or full overlap on the target role's distinctive
tokens after stripping both the existing seniority/role-noise stoplist and a new, narrow
`BROAD_TITLE_WORDS` filler-word stoplist — software, application(s), system(s), solution(s),
technology, technical, it). Sector was deliberately excluded as a qualifying signal (it's cross-role
and non-discriminating — e.g. a healthcare sector preference would equally "qualify" an unrelated
software job at a hospital). The dashboard (`list-ranked-jobs.sql`, `main.jsx`) now partitions jobs
into Recommended (Featured/Top matches) vs. Explore other results using this flag via a new pure
`frontend/src/dashboardRecommendation.js`, with a distinct "no strong matches yet" empty state when
jobs exist but none qualify.

No scoring math changed and no data backfill was needed: `ScoringWriter` unconditionally rewrites
`job_score`/`job_role_score` on every Find Jobs run, so the next run per workspace self-heals under
the new policy version; existing rows default to `qualifies_recommended = FALSE` until then
(fail-closed).

Verification: 164 Java tests (155 baseline + 9 new — pure-method unit tests for the title-qualification
predicate plus two new integration fixtures reproducing both originally-reported cases, asserting
`total() > 0` but `qualifiesRecommended() == false`) and 21 frontend tests (17 baseline + 4 new), 0
failures; migration V30 applied cleanly from an empty Testcontainers database; `npm run build`
succeeded. The existing Frontend/Backend/AI_ML/Sales calibration-pack tests and the Nurse
universal-policy test (previously only asserting `total().isPositive()`, the exact weak-assertion
pattern this bug exposed) now assert `qualifiesRecommended() == true` as an explicit non-regression
control. Not yet deployed to the production Render demo — committed locally, pending owner review
before push.

## 2. S0.2 Onboarding Correctness — selected, implementation verified, acceptance open

The owner selected S0.2 on 2026-09-09. Its requirement and design record is
[S0_2_ONBOARDING_CORRECTNESS.md](S0_2_ONBOARDING_CORRECTNESS.md). The implemented slice adds
deterministic résumé contact-noise correction, accurate professional-summary evidence, hyphenated
Front-end role aliases, searchable skill/role/sector editors, a versioned canonical sector catalogue
with workspace-private additions, country-wide search markets, progressive evidence review, and
same-browser-session draft recovery. It does not include S0.3 recommendation/ranking semantics.

The clean suite passed with 136 Java tests and 12 React tests against fresh PostgreSQL V1–V26. A
focused 38-Java-test suite, provider contract checks, packaging, live Compose health/OpenAPI checks,
and desktop/mobile headless Chrome inspection also passed. The redacted engineering fixtures measure
100% recall and precision for 14 expected explicit skills across four professions. S0.2 remains open
until the owner reviews those fixture expectations, representative PDF/DOC/DOCX files through the
5 MB boundary establish parsing p95, and final assistive-technology review is recorded. Do not mark
the checkpoint complete or begin S0.3 before those gates are resolved or explicitly excepted.

A final self-review fixed the remaining catalogue keyboard edge case: Escape now fully closes the
popup, and workspace-private additions participate in the same Arrow/Enter option sequence as
catalogue matches. The added React regression is included in the 12-test total above. The subsequent
owner UI follow-up caps long suggestion lists at 18rem with contained vertical scrolling and keeps
the active Arrow-key option in view.

## 3. Assisted multi-market onboarding follow-up — complete

Status: **implemented and verified on 2026-09-08.** The change closes `BUG-ONBOARDING-01` and
`BUG-ONBOARDING-02` without reopening the R1.1 route-migration scope.

### Owner-visible outcomes

- `/setup` no longer truncates `preference.searchMarkets` to its first entry. React owns an array of
  rows, renders every saved country/location pair, and submits every reviewed row to the existing
  activation API.
- Users can add, edit, and remove rows, with one required row and the existing ten-market maximum.
  Country changes seed that row's editable city/region from the country name and do not alter the
  user's current location.
- Client and server validation reject blank, unsupported, over-limit, and case-insensitive duplicate
  markets with product-safe messages. `SearchTarget.parse` now rejects rather than silently removes a
  duplicate. Existing normalized persistence, independent provider fan-out, ownership, and restart
  behavior are unchanged.
- The initial page remains résumé-first: profile, market, readiness, and advanced controls render only
  after a profile exists. Deterministic résumé role/skill suggestions remain editable evidence, not
  silently confirmed intent.
- A new profile's current location and first market use this visible policy: supported browser time
  zone, then supported browser language/region, then the labelled Singapore fallback. Browser time
  zone can provide a city-like estimate such as Sydney; language fallback uses the country name. A
  **Use browser estimate** action is available, every value remains editable, and the UI names the
  estimate source. No GPS, IP lookup, external service, or ungrounded résumé-location extraction was
  introduced.
- **Where you live now** is separate from **Places you want to search**, with an explicit note that
  current location is not another search-market filter. Preferred sectors sit with direction.
  **Advanced search preferences** is explicitly optional and once again exposes provider query
  override, pages per source, employment type, and work arrangement with sensible defaults and plain
  explanations.
- Readiness acknowledgement remains an explicit unchecked user action whenever required. The code
  does not auto-acknowledge or infer consent.

### Code and contract landmarks

- `frontend/src/Setup.jsx`: market-array state, add/edit/remove component, normalized payload,
  client validation, browser estimate policy, current/search location separation, and complete
  advanced controls.
- `frontend/src/styles.css`: responsive market rows and current-location/advanced-preference layout.
- `frontend/src/messages.js`: displays the API's safe `INVALID_PROFILE_REQUEST` detail.
- `src/main/java/com/ankit/joblens/onboarding/SearchTarget.java`: duplicate rejection.
- `ResumeProfileController.ActivationRequest`: explicit one-to-ten market list validation.
- `CandidateProfileExceptionHandler`: safe malformed-market JSON response without Jackson/framework
  details.
- `frontend/src/Setup.test.jsx`, `frontend/src/api.test.js`, `SearchTargetTests`, and
  `ResumeProfileControllerTests`: focused UI, location-policy, message, domain, and API coverage.

### Verification and review evidence

- `./mvnw clean test`: **BUILD SUCCESS**, 130 Java tests and 8 React/Vitest tests, 0 failures,
  0 errors, 0 skipped. Fresh PostgreSQL 17 Testcontainers applied Flyway V1–V25.
- Focused Java and frontend runs passed before the full suite. Tests cover two editable/submitted
  markets, normalization, duplicate and blank rejection, résumé-first rendering, and time-zone →
  browser-language → Singapore fallback behavior.
- `./mvnw -q spotless:apply` and `git diff --check` passed. No migration or dependency was added.
- After `./mvnw -q -DskipTests package`, `docker compose build app` and recreation succeeded. The
  first build attempt immediately after `clean test` correctly failed because that lifecycle does
  not produce `target/joblens.jar`; packaging resolved it. The live app reports `UP`, validates
  Flyway V25, and serves `index-DdhNRvf8.js` plus `index-BG1Cp8qN.css`.
- A temporary headless Chrome inspection added a second market in client state only. At 1440×1000
  and 390×844, both rows remained inside the viewport with no horizontal overflow; both remove
  controls, the add control, distinct current/search labels, and every advanced control were present.
  It did not submit the form or mutate database data.

### Scope and next-session boundary

No provider, Batch, scoring, schema, identity, privacy, deployment, or run-control behavior changed.
The browser estimate is deliberately advisory and may be wrong for VPNs, travel, generic locales, or
unmapped time zones; the visible source label and required user review are the compensating control.
Do not add IP/GPS lookup or résumé-location extraction without a separately reviewed privacy and
accuracy contract. S0.2 is now the selected checkpoint; do not combine it with another milestone.

## 3. React surface checkpoint — R1.1

The owner selected and completed the React migration for `/dashboard`, `/setup`, and `/applications`.
A Vite-built React bundle is embedded in the Spring Boot JAR. Setup provides editable resume-derived
suggestions and explicit readiness acknowledgement; applications uses workspace-scoped lifecycle and
follow-up APIs. Maven provides pinned project-local Node tooling and a committed lockfile; no global
Node installation is required. Verification on 2026-09-08: `./mvnw clean test` passed (120 Java tests
and 2 React/Vitest tests), with fresh PostgreSQL 17 Testcontainers through Flyway V24; Spotless and
`git diff --check` also passed. At that R1.1 checkpoint S0.1 remained incomplete; its later completion
evidence is recorded in the resume checkpoint above and in `BUILD_PROGRESS.md`.

## 3. D1 startup requirements re-baseline

The earlier “personal learning application” boundary is superseded. JobLens is a multi-user SaaS
startup for job seekers. Spring Batch remains a useful durable execution mechanism, but it is not the
product purpose. The current anonymous Compose application is the verified JobLens V1 development foundation and
must not be called production-ready, corporate-ready, or complete.

Planning assumptions—not demand forecasts or load-test claims—are:

| Stage | Accounts | DAU | Peak sessions | Discovery commands/day |
| --- | ---: | ---: | ---: | ---: |
| Private beta | 5,000 | 500 | 100 | 1,000 |
| Year one | 50,000 | 5,000 | 750 | 10,000 |
| Stress point | 250,000 | 25,000 | 3,000 | 50,000 |

Year-one technical targets include 200 peak API requests/second, five admitted discovery
commands/second, two million shared normalized jobs, 25 million candidate sightings/scores, 99.9%
monthly authenticated-API availability, read p95 below 300 ms, mutation p95 below 500 ms, discovery
acknowledgement below two seconds, progress freshness below five seconds, RPO at most five minutes,
and RTO at most sixty minutes. None is currently verified by load or recovery testing.

The current search model would make about 18 provider page calls for a median two-role,
three-market, three-page run, or roughly 180,000/day at the year-one command estimate before retries.
The target architecture therefore shares provider ingestion by normalized query fingerprint and
freshness window, enforces provider budgets/admission control, and keeps candidate intent, ranking,
feedback, and applications private.

Real-time means a product command returns a run ID quickly and exposes committed progress through SSE
with polling fallback while workers continue durable processing. It does not mean synchronous
provider crawling. The modular monolith remains: one codebase may run as separately scaled stateless
API and worker roles. A broker, search engine, or microservice requires measured throughput,
fault-isolation, deployment, data-ownership, or team-ownership pressure.

### Fixed versus not fixed

| Area | State | Summary |
| --- | --- | --- |
| PostgreSQL/Flyway and modular codebase | FIXED foundation | Incremental schema, explicit SQL, transactional boundaries |
| Raw landing and deterministic Batch processing | FIXED foundation | Bounded source calls, restart, checkpoints, normalization, skills and duplicate evidence |
| Profile/intent/market/ranking/application flows | PARTIAL | Functional V1 implementation, but open correctness/UX defects and no authenticated ownership |
| Source ecosystem | PARTIAL | Legitimate adapters exist; provider contracts, quotas, shared acquisition and sustainable costs are unresolved |
| Job Explorer and ranking quality | PARTIAL | Explainable scores exist; filters/keyset paging, feedback calibration and cross-role accuracy remain |
| Identity/RBAC/account recovery | MISSING launch blocker | Anonymous UUID cookie is not authentication |
| Product run queue/concurrency/recovery/live progress | PARTIAL launch blocker | Single-process product run IDs, safe concurrency, stale recovery and committed diagnostics exist; distributed admission, cancellation and SSE do not |
| Privacy/storage/export/deletion | MISSING launch blocker | Original résumé storage, scanning, retention and account workflows do not exist |
| Production platform | MISSING launch blocker | No CI/CD, production environment, managed HA database, restore evidence, centralized observability or security/load testing |

Current local scale is only 14 anonymous workspaces, 1,528 raw jobs, 1,527 normalized jobs, 22 search
runs, 47 Batch executions, two applications, and a 36 MB database. This is evidence of behavior, not
capacity.

Startup delivery is indexed as D1/D2 followed by S0–S6. S0.1 Discovery Execution Safety completed on
2026-09-08. V24 records source query/count/first-zero-stage diagnostics, V25 adds the product `STALE`
state, and the service now projects product run IDs/statuses, reconciles concurrent same-command
launches, and recovers orphaned executions after the configurable 30-minute threshold. This is a
single-process safety boundary; distributed admission, leases, cancellation and live progress remain
in S2.

## 3. M3.2 completed slice — exact handoff

### Product and architecture decision

M3.2 does not divide professions into "supported" roles and a weaker generic algorithm. Every one of
the candidate's one to three ordered target roles is evaluated by the same deterministic,
versioned `universal-v1` policy. A mapped role also receives an additive, versioned calibration
overlay containing curated title and skill evidence. An unmapped catalogue or workspace-private role
still receives the full universal policy using its selected title, aliases, confirmed candidate
skills, sectors, seniority, markets, work arrangement, employment type, salary availability, and
freshness.

The overlays are transparent starter rules, not a claim of measured ranking accuracy. The S4
calibration checkpoint must use user-reviewed Fit/Maybe/Not-fit examples and sample-qualified
Precision@10 before changing weights or claiming improvement. There is no LLM, automatic
self-training, forced role classification, or hidden job rejection.

### Deterministic scoring contract

For every normalized job, scoring loads the candidate and ordered target roles once at step creation,
then calculates one independent 0–100 score per role. The highest total becomes the best-role
projection. A tie is resolved by the user's role priority: primary before secondary before
exploratory.

| Dimension | Maximum | Current deterministic evidence |
| --- | ---: | --- |
| Role title | 25 | Exact selected role 25; catalogue alias 23; primary overlay title 22; related overlay title 18; full distinctive-token match 18; partial token match 10 |
| Skills | 15 | Confirmed candidate skills up to 8, plus overlay core 4, preferred 2, and supporting 1 |
| Optional sector | 15 | Three points per matched preferred-sector term, capped at 15 |
| Seniority | 10 | Exact explicit level 10; adjacent level 6; unspecified policies receive bounded partial points |
| Location/work arrangement | 10 | Selected market 5 plus accepted remote/hybrid/onsite arrangement 5, with separate reason rows |
| Employment | 10 | Exact requested type 10; `ANY` 5; missing provider value 3 |
| Salary availability | 10 | Provider supplied salary 5; missing salary uses the bounded configured default, currently 3 |
| Freshness | 5 | Full points through the configured fresh window, partial points through the second window |

Persisted reason points reconcile exactly to each role total. A missing core skill produces an
explicit zero-point `CALIBRATED_CORE_SKILLS` reason; it does not filter or discard the posting. The
existing `job_score.technical_score` remains a compatibility projection of title plus skills, and
`domain_score` remains the sector score, preserving the earlier database constraint and API shape.

### Initial version 1.0.0 calibration overlays

| Overlay | Mapped roles | Curated skill levels |
| --- | --- | --- |
| `FRONTEND` | Frontend Engineer | Core: HTML, CSS, JavaScript; preferred: TypeScript, React, Angular, Vue.js; supporting: Next.js, Redux, Web Accessibility |
| `BACKEND` | Backend Engineer | Core: Java, Spring Boot, SQL; preferred: REST, PostgreSQL; supporting: Kafka, Docker |
| `AI_ML` | Data Scientist, AI Engineer, Machine Learning Engineer | Data Scientist: Python, Machine Learning, Statistics; SQL and Data Analysis preferred. AI Engineer: Python and Machine Learning core, Docker supporting. ML Engineer: Python and Machine Learning core, SQL preferred, Docker supporting |
| `SALES_CUSTOMER_SUCCESS` | Sales Manager, Sales Executive, Account Executive, Customer Success Manager, Customer Success Specialist | Sales/CRM signals according to role; Customer Service/CRM for success roles; Stakeholder Management preferred for Sales Manager and Customer Success Manager |

Curated title alternatives include UI Engineer/UI Developer/Web UI Engineer, API Engineer/Server-side
Engineer, Machine Learning Scientist/Generative AI Engineer/Applied ML Engineer/MLOps Engineer, and
the relevant Sales Lead, Sales Representative, Account Sales Executive, Client Success Manager, and
Customer Success Associate variants. Flyway V23 also seeds missing canonical roles for AI Engineer,
Machine Learning Engineer, Sales Executive, Account Executive, and Customer Success Specialist, plus
the bounded aliases needed by these mappings.

### Persistence and restart behavior

Flyway `V23__create_role_aware_ranking.sql` adds:

- versioned `role_calibration_pack`, role mapping, title-signal, and skill-signal tables;
- candidate-private `job_role_score` and `job_role_score_reason` tables;
- best target-role, ranking-policy, and calibration-pack identity on `job_score`;
- constraints for score ranges, dimensional totals, role priority, identity, and active pack version.

The scoring writer replaces all role scores/reasons for one job and candidate inside the Batch chunk
transaction, then updates the best-role projection and scored content hash. Rerunning unchanged input
does not create duplicate role scores. The pack code/version and `universal-v1` policy are persisted
with the result so the explanation remains inspectable even when future packs exist.

Both normal Find Jobs launches and direct intelligence API launches add
`rankingPolicyVersion=universal-v1` as an identifying Spring Batch parameter. A future scoring-policy
change must bump this constant and the relevant pack version so it creates a new logical JobInstance
instead of being mistaken for the prior calculation.

V23 does not silently recalculate historical `job_score` rows during application startup. Existing
rows retain `ranking_policy_version=legacy-v1`, have no `job_role_score` children, and appear as **Not
rescored** on the dashboard until the user runs Find and rank jobs or launches intelligence with a new
business date. This preserves migration safety and keeps recalculation an explicit Batch action.

### Inspection behavior

- `GET /api/jobs` includes the best target role and ranking/overlay versions in each ranked row.
- `GET /api/jobs/{id}` additionally returns `roleScores`, every dimension, and nested point reasons
  for each selected role, ordered by score and then user priority.
- The dashboard's Top scored jobs table names the best target role and either the active pack/version
  or `Universal`; old rows are clearly labelled **Not rescored**.
- The scoring step remains part of the existing six-step `findJobsJob`; no provider, scheduler,
  service boundary, or microservice was added.

### Verification evidence

- `./mvnw clean test`: 111 tests, 0 failures, 0 errors, 0 skipped.
- Fresh PostgreSQL 17 Testcontainers applied all 23 Flyway migrations.
- Focused coverage proves Frontend versus Backend separation and best-role choice, AI/ML, Sales,
  Customer Success, the universal-only Registered Nurse path, missing-core-skill behavior, reason-sum
  invariants, per-role persistence, idempotent rerun, and existing Find-jobs failure/restart behavior.
- `./mvnw -q -DskipTests package`, `docker compose build app`, and forced app recreation succeeded.
- The recreated app validated V23; `/actuator/health` returned `UP`; PostgreSQL reported active
  version `1.0.0` packs `AI_ML`, `BACKEND`, `FRONTEND`, and `SALES_CUSTOMER_SUCCESS`.
- Formatting and repository checks passed: `spotless:check`, `git diff --check`, and a clean worktree
  after commit `2885c84`.

### Deliberately not included in M3.2

The former M3.3 Job Explorer scope has not started and is now part of S4. It owns backend SQL/API
filtering by country/search market, source, freshness, work mode, and saved state;
Recommended/Newest sorting; country grouping; and stable 20-row keyset **Load more** pagination that
preserves filter/sort state. These operations should be performed in PostgreSQL, with the browser
limited to rendering and interaction.

The former M3.4 scope has not started and is now part of S4. It owns workspace-private
Fit/Maybe/Not-fit feedback, optional reason codes, auditability, reviewed fixture samples,
false-positive analysis, and Precision@10 reporting by role pack, market, and scoring version. M2.8
Typed SQL Resource Registry remains separately deferred. Do not start any of these without an
explicit user checkpoint.

### Open observed bugs and UX gaps

#### BUG-M3-001 — AI Engineer returns no integrated results

Status: **DIAGNOSED FOR S0.1 on 2026-09-08**. The original live ten-market execution was not retained,
so its provider-side cause remains unknown and must not be guessed. The controlled execution path and
the diagnostic evidence required for any recurrence are now verified.

Reproduction reported by the user:

1. Select/search for the target role **AI Engineer**.
2. The generated outbound LinkedIn search query looks correct.
3. Run integrated JobLens discovery with ten selected countries.
4. Adzuna and the JobLens ranked-results view return zero jobs for every country.

Known boundary: the outbound LinkedIn link and integrated Adzuna discovery are different execution
paths. A plausible-looking LinkedIn query proves only that the outbound query renderer produced an
expected string; it does not yet prove which terms, country routes, provider profiles, pages, raw
responses, freshness rules, normalization, workspace sightings, or ranking inputs were used by the
Adzuna/Find-jobs path.

Verified controlled evidence:

- An AI Engineer Singapore fixture persisted the generated query
  `AI Engineer Machine Learning Python`, called the expected Adzuna Singapore route, and reconciled
  one provider result through raw landing, normalization, workspace sighting and scoring.
- A Backend Engineer control in the same market persisted `Backend Engineer Java Spring Boot` and
  also reconciled through the complete pipeline.
- A legitimate empty provider response persists the source, market and query with
  `firstZeroStage=PROVIDER_RESPONSE`, allowing the UI/API to explain where the run became empty.

Evidence retained for a future live recurrence, without assuming the fault is ranking or Adzuna:

- the active candidate's ordered role intent and persisted generated query rows;
- all ten active Adzuna source profiles, including country, location, keywords, page limit, and query
  origin/version;
- the corresponding source-run statuses, pages attempted/fetched, received/new/changed/unchanged,
  raw/normalized/sighted/scored counts, and sanitized failures;
- sanitized provider request paths and Adzuna response counts for at least one affected country;
- whether rows reached raw landing but were removed by freshness, normalization, workspace-sighting,
  or user-result eligibility rules;
- a control run using a known broad role in one of the same countries, so provider/configuration
  failure can be separated from AI Engineer query relevance.

Do not broaden this report into a new provider, scraping, ranking-weight change, or country redesign.
Reproduce and locate the first stage whose count becomes zero before choosing a fix or assigning it to
a milestone.

#### BUG-M3-002 — Résumé title suggestions contain false positives, duplicates, and missed directions

Status: **IMPLEMENTED AND AUTOMATED-TESTED IN S0.2; owner fixture review remains open**. The original
report came from a user-supplied Senior AI Engineer résumé on 2026-09-03. The supplied résumé contains
personal contact and profile information; those values are deliberately not copied into this
repository. The minimum redacted evidence used to reproduce the problem is recorded below.

Relevant résumé structure:

- The document headline is `Senior AI Engineer`.
- The professional summary starts with `Senior software engineer` and describes production AI/agent
  systems, full-stack applications, Python, TypeScript, Go, SQL, REST, and gRPC.
- Recent experience contains `AI Engineer` and multiple explicit `Front-end Developer` positions.
- A contact/profile line has the form `Blog: medium.com/@...`.

Observed setup output:

- `AI Engineer — RESUME_HEADLINE — Senior AI Engineer` is shown twice as two separate suggestions.
- `Software Engineer — RESUME_HEADLINE` is derived from the professional-summary sentence.
- `medium — RESUME_HEADLINE — Blog: medium.com/@...` is suggested as a role with medium confidence,
  even though `medium` is the website host and not an occupation.
- No Frontend Engineer direction is shown in the reported suggestions despite explicit Front-end
  Developer experience entries.
- The rejected/ambiguous panel shows `AI — REJECTED: Senior AI Engineer`, plus short fragments such as
  `R` and `js` extracted from unrelated prose or skill lines. This is noisy and makes the valid AI
  evidence appear contradictory.

Implemented boundary: contact, social, and blog labels or URL hosts do not
become job-title suggestions; identical canonical suggestions should not be repeated; evidence source
labels should correspond to the actual résumé section; and explicit recent experience should be
eligible to suggest more than one plausible direction without silently selecting it as search intent.
Extractor version `esco-deterministic-v4`, the one-row-per-canonical-role key, and focused tests now
cover these cases. The final owner-reviewed fixture gate remains open.

#### UX-M3-003 — Preferred sectors need a selectable catalogue control

Status: **IMPLEMENTED AND AUTOMATED-TESTED IN S0.2; final acceptance remains open**.

The setup form currently makes preferred sectors difficult to enter consistently. The requested
direction is a searchable dropdown or multi-select backed by normalized sector records, while keeping
preferred sectors optional. The handoff must not assume whether this reuses an existing taxonomy,
adds a small curated sector catalogue, or permits workspace-private values; that data-model decision
must be made before implementation. Preserve selected values across profile versions and ensure the
same normalized values drive query generation and the optional sector ranking dimension.

The S0.2 implementation replaces the plain text field with a debounced searchable multi-select backed
by Flyway V26's versioned `joblens-sector-v1` catalogue, aliases, and workspace-private values. The
canonical compatibility projection continues to drive existing portal-query and scoring reads.

#### BUG-M3-004 — City/region is mandatory even when country is selected

Status: **IMPLEMENTED AND AUTOMATED-TESTED IN S0.2; final acceptance remains open**.

Starting behavior: setup required a city/region value even when the user had already selected a
supported country. The user expects country-only searches to be valid and city/region to narrow the
market only when supplied. V26 and `SearchTarget` now persist blank as country-wide. Adzuna omits its
optional `where`, while Jooble and outbound links derive the country display name from the validated
country key because their contract needs a location string. Focused provider, portal, API, and
PostgreSQL tests cover the boundary without manufacturing a city or exposing the ISO code for edits.

#### BUG-M3-005 — Find Jobs leaks a raw Spring Batch already-running error

Status: **FIXED for the S0.1 single-process boundary on 2026-09-08**.

Observed user-facing message when trying to run Find Jobs again:

```text
A job execution for this job is already running: JobInstance: id=36, version=0, Job=[findJobsJob]
```

The concurrency guard may be correct if an execution is genuinely active, but exposing JobInstance
IDs, versions, and the internal Batch job name is not an acceptable normal-user abstraction. It is
also unknown whether execution 36 was actively progressing, stuck, or merely represented by stale
state. A future diagnosis must inspect the JobExecution/StepExecution status, timestamps, exit
description, workspace run projection, and restart/abandon rules before changing concurrency logic.
The eventual user experience should distinguish an active search from a recoverable failed/stale run
and provide the appropriate status or recovery action without leaking framework internals. Retain
detailed Batch identity only in operator/admin inspection.

Read-only inspection on 2026-09-04 confirmed two direct leak paths: `FindJobsPageController` flashes
`exception.getMessage()`, and `BatchApiExceptionHandler` places raw Batch exception messages in the
REST error body. `FindJobsService` records `workspace_search_run` only after `JobOperator.start(...)`
returns, so a launch conflict can happen before the active execution is projected for the user. This
narrows the S0.1 design problem but does not establish why execution 36 remained active.

S0.1 now returns a workspace-owned product `runId`, a product-safe status and bounded outcome; normal
UI/API responses no longer expose JobInstance/JobExecution IDs, versions or internal job names. A
real two-thread test verifies that simultaneous identical commands reconcile to one Batch execution
and one product run. An orphaned execution older than the configurable 30-minute threshold is marked
`STALE`, recovered and restarted on the same JobInstance so committed checkpoints remain usable.
The guard is intentionally process-local; cross-node leases/admission and live cancellation remain an
S2 launch requirement.

#### BUG-M3-006 — .NET Engineer ranks highly for Java Backend and unrelated Frontend profiles

Status: **OPEN and investigated read-only on 2026-09-09; not implemented**. The current local data
proves the recommendation-presentation defect and reproduces broad-role/baseline scoring risk, but it
does not retain the exact owner-reported Java Backend and Frontend candidate profiles. This remains a
ranking-quality query rather than evidence that one isolated weight or matcher is already the fix.

Read-only local evidence from the active candidate and 601 dashboard-eligible jobs:

- Five retained `.NET` listings were dashboard-eligible but ranked only 225–325, so none entered the
  current 25-row dashboard response. The active candidate targets AI Engineer, Senior AI Engineer,
  and Software Engineer rather than either exact reported profile; this is not a complete reproduction.
- `Backend Software Engineer (.NET) - YZ11` scored 43 for Software Engineer: 25 role-title, 5
  seniority, 5 location, 5 employment, and 3 missing-salary points, with zero confirmed skill,
  calibrated skill, sector, work-arrangement, and freshness contribution. Its normalized content hash
  is `152e74b64ab1a76f3589515c310aaf7d5bc8f71b04d61ebadcf6824a339f4c3e`.
- Other retained `.NET` listings scored 42–46 principally because the exact or alias-level broad
  `Software Engineer` title earned 23–25 points. A Full Stack `.NET` listing with no role-title match
  still scored 19 from one confirmed SQL match plus seniority/location/employment/salary baseline.
- The inspected `job_skill` rows contain Java, JavaScript, Microservices, Angular, CSS, TypeScript,
  Docker, and SQL where applicable, but no `.NET`, `C#`, or `ASP.NET`. This establishes a taxonomy/
  extraction coverage gap, not by itself a safe negative-scoring rule.
- The formula does not reward generic `Engineer` as a fallback token when a distinctive target-role
  token exists. The observed 25 points come from matching the full broad target phrase `Software
  Engineer`; a no-role/no-skill result can still receive roughly 30–35 non-fit baseline points under
  favorable preferences.
- `list-ranked-jobs.sql` has no recommendation qualifier or threshold and converts missing scores to
  zero. React always labels row one `Featured for you` and rows one through eight `Top matches`.
  Therefore the presentation defect is proven even though today's retained `.NET` rows are outside
  the response limit.
- Existing tests select the best target-role projection but do not include reviewed `.NET` Fit/Maybe/
  Not-fit controls, recommendation qualification, Precision@10, or dashboard presentation assertions.

Before implementation, add the missing Java Backend and Frontend comparison fixtures and define a
versioned qualification decision distinct from raw additive score. Do not derive a threshold from the
retained active profile or treat the local rank positions as the owner reproduction.

User-reported reproductions:

1. A candidate whose profile and résumé are predominantly Java and backend engineering receives a
   high-ranked `.NET Engineer` job suggestion.
2. A Frontend Engineer candidate with no reported .NET experience also receives a `.NET Engineer`
   suggestion with a high score.

Expected behavior to evaluate: a posting with neither a meaningful target-role relationship nor
relevant confirmed/calibrated skills should not appear strongly recommended merely because it
matches non-role dimensions such as market, work arrangement, employment type, salary availability,
freshness, or generic words such as `Engineer`. JobLens may retain such a posting as a low-confidence
result, but the score and explanation must not imply strong candidate fit without supporting role or
skill evidence.

Evidence required before selecting a fix:

- the exact normalized `.NET Engineer` title, description, extracted `job_skill` rows, and content
  hash;
- each affected candidate's ordered target roles, confirmed skills, sectors, markets, and
  preferences;
- every persisted `job_role_score` dimension and `job_role_score_reason`, including which target role
  became the best projection;
- whether `.NET`, `C#`, `ASP.NET`, or related aliases were actually extracted or matched;
- the contribution from role title, confirmed skills, calibrated skills, sector, seniority,
  location/work arrangement, employment, salary, and freshness;
- what the user interface means by “high”: an absolute threshold, relative ordering in a weak result
  set, or only a visually prominent raw score;
- a comparison fixture containing a genuine Java Backend job and a genuine Frontend job under the
  same non-role preferences.

Do not immediately add a hard `.NET` exclusion or tune only the Backend/Frontend overlays. First
determine whether the defect is generic-title matching, excessive non-role baseline points, missing
negative evidence, absent skill extraction, best-role projection, or presentation/ranking semantics.
Any scoring change must remain deterministic, explainable, versioned, and tested against unrelated
professions that use the universal-only policy.

#### BUG-ONBOARDING-03 — React role and sector suggestions are no longer searchable

Status: **IMPLEMENTED AND AUTOMATED/BROWSER-TESTED IN S0.2; final acceptance remains open**.

Owner report: role and sector fields had stopped offering useful suggestions. Starting code
inspection confirmed two distinct gaps:

- The backend still exposes `GET /api/candidate-profile/roles/catalog?query=...` and returns up to 100
  shared or workspace-private normalized roles. React `Setup.jsx` never calls it. Its role editor
  shows only the finite `intelligence.roleSuggestions` extracted during the last résumé upload, then
  accepts typed text as a direct addition. When extraction finds no direction—or the user wants a
  different direction—the existing catalogue is effectively invisible.
- Preferred sectors are a plain optional string. There is currently no sector catalogue/API or
  normalized React selector, matching the already-recorded `UX-M3-003` design gap.

Acceptance criteria before calling this fixed:

1. Typing in Target roles queries the owned role catalogue with bounded/debounced requests and shows
   deterministic results without replacing résumé-evidence suggestions.
2. The control supports keyboard navigation, visible focus, loading, no-match, recoverable-error,
   deduplication, the three-role limit, removal, and an explicit workspace-private custom-role path.
3. Résumé-derived roles remain labelled as evidence and are never silently confirmed. Catalogue
   results and extracted evidence must not produce duplicate chips.
4. Preferred sectors become an optional searchable multi-select only after deciding the normalized
   source, aliases, workspace-private policy, versioning, and migration compatibility. The same
   values must feed provider-query generation and sector-score explanations.
5. Focused React/API/accessibility tests and PostgreSQL profile-version persistence evidence pass;
   direct-route and responsive behavior remain intact.

#### UX-DASHBOARD-01 — Recommendation board does not define a strong-relevance boundary

Status: **OPEN and inspected on 2026-09-09; not implemented**. Qualification/scoring belongs to S0.3;
the broader explorer remains S4.

Current logic, based on `DashboardApiController` and `sql/dashboard/list-ranked-jobs.sql`:

1. Resolve the current workspace's active candidate profile.
2. Keep only jobs sighted by that workspace through an active source profile. Exclude stale Adzuna
   rows beyond the configured maximum age.
3. Require at least one meaningful generated-query token to appear in the normalized title, unless
   that source profile contains no meaningful token after generic seniority/title words are removed.
4. Left-join the candidate's score, treating an absent score as zero; order by score descending and
   normalized-job ID; return at most 25 rows.
5. React presents the first row as **Featured for you** and the first eight as **Top matches**.

Therefore the board currently shows the highest-scoring eligible jobs available to that workspace,
but “highest available” is not the same as “strongly relevant.” There is no minimum recommendation
threshold, no requirement for positive role/skill evidence, and no separate low-confidence or newly
discovered section. A weak or unscored result can still occupy a prominent slot when the set is weak.

Required product contract:

- **Recommended** must mean the job meets a reviewed, versioned qualification rule with meaningful
  role/title or skill evidence; location, freshness, salary availability, and other baseline points
  cannot by themselves imply strong fit.
- If no job qualifies, show an honest no-strong-matches state and optionally a clearly separate
  **Explore other results** or **Newly discovered** section. Do not feature the least-bad item as a
  recommendation.
- Each recommended card must expose the selected best target role and a concise reason, with complete
  persisted scoring evidence available on inspection.
- Define whether viewed/saved jobs remain in Recommended, how ties and stale scores behave, and when
  a changed profile requires rescoring before display.
- Verify with a reviewed Fit/Maybe/Not-fit fixture set across multiple professions and markets. Track
  Precision@10 and coverage; do not choose a threshold from one hand-picked résumé.
- S4 may add Recommended/Newest sorting, filters, grouping, and stable keyset pagination only after
  this semantic boundary is established.

#### UX-ONBOARDING-04 — Premium résumé-first intelligence experience

Status: **IMPLEMENTED AND AUTOMATED/BROWSER-TESTED IN S0.2; owner fixture, parsing-p95, and final
assistive-technology acceptance remain open**.

The owner wants the upload and review experience to feel as smooth, calm, and detailed as a premium
Apple product experience, especially in how it captures the majority of meaningful résumé keywords.
This is a quality and interaction reference, not permission to copy Apple branding, product names,
assets, text, page composition, animations, or trade dress. JobLens must retain its own identity.

Required experience direction:

- Keep one dominant résumé upload action with generous visual hierarchy and progressive disclosure.
  Show the selected file name/type/size immediately, allow replacement, and avoid presenting the full
  review surface until parsing produces a usable result.
- Acknowledge the upload interaction visually within 100 ms. Show honest stages such as uploading,
  reading, matching profile evidence, and preparing review; do not fabricate percentage progress
  when the server cannot supply it. Provide explicit success, partial/readability-warning, invalid,
  retry, and safe-replacement states.
- Present a detailed but scannable extraction summary: confirmed/detected skills and tools, role
  directions, sectors, seniority, locations, and other supported keyword groups. Every suggestion
  should expose bounded source evidence and confidence/status on demand, while the default view stays
  calm rather than becoming a dense diagnostics page.
- Users can accept, edit, remove, search, and add values with undo-safe interactions. Inferred values
  remain unconfirmed until activation, readiness acknowledgement remains explicit, and unsupported or
  ambiguous text is accounted for rather than silently disappearing.
- Respect keyboard and screen-reader operation, visible focus, contrast, reduced-motion preference,
  responsive layouts, and stable content placement. Motion should clarify state changes, not decorate
  or delay work.
- Preserve the current privacy statement: the original file is not retained in this development
  baseline. Do not add storage, third-party analytics, an LLM, or external résumé processing under
  this UX requirement.

Quality evidence required before implementation is called complete:

1. Build a redacted, owner-reviewed fixture set spanning several professions and document expected
   explicit skills/tools, plausible roles, false-positive traps, and ambiguous terms.
2. Measure precision and recall for explicit active-taxonomy keywords. Initial acceptance target:
   at least 90% recall and 95% precision across that reviewed set, with every omitted/rejected term
   inspectable. Reconfirm these thresholds when the fixture set is approved; do not claim résumé
   understanding from raw keyword count alone.
3. Benchmark supported files through the 5 MB limit and set a parsing-completion p95 after observing
   representative PDFs/DOCs/DOCXs. Immediate feedback, cancellation/replacement safety, and no lost
   review edits are required independently of backend latency.
4. Test success, empty, partial, warning, failure, retry, replacement, Back/Forward, refresh,
   desktop/mobile, reduced-motion, keyboard, and screen-reader-labelled paths.
5. Keep extraction deterministic and versioned unless a later separately approved milestone defines
   an external/AI trust, privacy, cost, evaluation, and fallback contract.

## 4. Product flow that works now

```text
/setup
  → upload readable PDF/DOC/DOCX and receive versioned machine-readability findings
  → acknowledge review-required evidence for suspicious or unusual readable documents
  → review evidence-backed skills and titles, searchable private additions, current city,
    and country-name/desired-location rows in one form
  → save and activate the reviewed profile in one transaction

/dashboard
  → Find and rank jobs
  → findJobsJob
      1. public-source discovery
      2. raw JSON landing
      3. normalization
      4. skill extraction
      5. exact and fuzzy duplicate analysis
      6. universal role-aware scoring with optional calibrated overlays and per-role reasons
  → open original listing (records VIEWED)
  → save an application

/applications
  → controlled lifecycle transitions
  → refresh deterministic follow-ups
  → complete reminders
```

Each anonymous browser workspace owns its candidate profile, preferences, sightings, scores, views,
applications, and follow-ups. The browser cookie is the current identity boundary.

## 5. Current implementation architecture

The current V1 implementation is a batch-first modular monolith: one Spring Boot application, one PostgreSQL
database, and one deployable image. The target keeps the modular codebase while allowing separately
scaled API and worker runtime roles.

```text
Thymeleaf / REST
       ↓
application services and ownership checks
       ↓
Spring Batch jobs and steps
       ↓
source adapters → raw landing → deterministic intelligence → lifecycle projections
       ↓
PostgreSQL owned by Flyway
```

Important jobs:

| Job | Purpose |
| --- | --- |
| `findJobsJob` | Normal one-click workspace flow from discovery through scoring |
| `searchProfileImportJob` | Legacy/operator CSV import workflow |
| `jobDiscoveryJob` | Operator launch of discovery only |
| `jobIntelligenceJob` | Operator launch of normalization through scoring |
| `applicationFollowUpJob` | Candidate-scoped deterministic reminders |
| `weeklyMarketInsightJob` | Shared weekly market aggregates |

Automatic Batch startup remains disabled. Jobs use meaningful identifying parameters, persisted
ExecutionContext checkpoints, observable failures, restarts, and idempotent writes.

## 6. Job sources

| Source | Current behavior | Credential |
| --- | --- | --- |
| Adzuna | Default broad public discovery source | `ADZUNA_APP_ID`, `ADZUNA_APP_KEY` |
| Jooble | Optional broad source; live Singapore flow verified | Regional `JOOBLE_API_KEY` |
| Greenhouse | Automatically detected only from direct official board URLs and validated internally | None |
| Lever | Automatically detected only from direct global official hosted URLs and validated internally | None |
| LinkedIn | Three smart outbound searches using role/sector/technology Boolean queries | None |
| JobStreet Singapore | Three smart outbound searches using concise natural queries | None |
| SEEK Australia/New Zealand | Three smart outbound searches per region using concise natural queries | None |

LinkedIn, JobStreet, Indeed, and Google results are not scraped. Provider JSON from integrated APIs is
stored before provider-specific normalization. Live Jooble acceptance completed on 2026-08-31 with
60 raw, normalized, sighted, and scored Singapore records in Find Jobs execution 22.

To enable Jooble:

```env
JOOBLE_API_KEY=your-singapore-regional-key
```

Rebuild/restart the app, then save and confirm preferences again so the workspace receives a Jooble
search profile.

## 7. Data and processing decisions

- Flyway V1-V23 owns application and Spring Batch metadata schemas.
- Search countries/locations are normalized as independent `workspace_search_target` rows. Confirming
  preferences creates one provider profile and checkpoint per supported source/market combination.
- Spring JDBC is used; JPA and Lombok are intentionally absent.
- Complex or reused SQL lives under `src/main/resources/sql/` and is loaded with named parameters.
- Original resume bytes are not stored. Only validated metadata, extracted text-derived profile data,
  and SHA-256 identity are retained.
- Exact duplicate evidence is source/external ID or normalized-content hash.
- Fuzzy matches are explainable review suggestions, not probabilities or automatic merges.
- Scoring applies deterministic `universal-v1` dimensions to every selected role. Frontend, Backend
  Engineering, AI/ML, and Sales/Customer Success add versioned title/skill evidence; other catalogue
  and workspace-private roles retain the same universal policy. The best role projects to
  `job_score`, while all candidate-private alternatives and reasons persist in `job_role_score`.
  Workspace-private custom skills are matched directly without entering the global extraction
  catalogue.
- Unknown end clients are not guessed. Any future estimate must expose evidence and uncertainty.

## 8. Main inspection points

```text
Application: http://localhost:8080
Setup:       http://localhost:8080/setup
Dashboard:   http://localhost:8080/dashboard
Swagger:     http://localhost:8080/swagger-ui.html
OpenAPI:     http://localhost:8080/v3/api-docs
Health:      http://localhost:8080/actuator/health
```

Useful APIs:

```text
POST /find-jobs
GET  /api/batch/find-jobs/runs
GET  /api/batch/find-jobs/runs/{runId}
POST /api/batch/find-jobs/runs/{runId}/restart
GET  /api/jobs
GET  /api/jobs/{id}
GET  /api/duplicates
GET  /api/duplicates/similarities
GET  /api/source-boards
GET  /api/candidate-profile/intelligence
GET  /api/candidate-profile/skills/catalog?query=react
GET  /api/candidate-profile/roles/catalog?query=product
GET  /api/candidate-profile/countries
GET  /api/job-views
GET  /api/applications
GET  /api/follow-ups
GET  /api/batch/executions
```

## 9. Code map

```text
src/main/java/com/ankit/joblens/
  batchapi/       launch, history, job inspection, OpenAPI descriptions
  workspace/      anonymous workspace identity and candidate ownership
  onboarding/     resume validation, profile drafts, skills, preferences
  discovery/      source adapters, raw landing, source boards, Find-jobs orchestration
  intelligence/   normalization, skills, duplicate analysis, scoring
  lifecycle/      applications, transitions, follow-ups
  dashboard/      Thymeleaf controllers and view tracking

src/main/resources/
  db/migration/   Flyway V1-V25
  sql/            externalized SQL grouped by feature
  templates/      setup, dashboard, applications
```

## 10. Verification commands

```bash
./mvnw -q spotless:apply
./mvnw clean test
git diff --check

./mvnw -q -DskipTests package
docker compose build app
docker compose up -d
curl http://localhost:8080/actuator/health
```

Testcontainers requires Docker Desktop. Never commit `.env`, credentials, tokens, or resume data.

## 11. Known limitations

- Anonymous cookie workspaces have no account recovery or cross-device synchronization.
- Jooble needs a regional key and its provider quota must be monitored.
- Jooble runs only for the market matching `JOOBLE_COUNTRY_CODE`; Adzuna provides the other configured
  broad-market searches.
- Greenhouse enrichment activates only when a legitimate source exposes a direct official board URL.
- Lever enrichment activates only for a direct global `jobs.lever.co` URL; tracking redirects and the
  separate EU host are deliberately unsupported.
- Discovery is sequential and unpartitioned in the current V1 implementation; it does not meet the startup
  provider-budget or concurrency target.
- Fuzzy thresholds and the initial role-overlay signals still need reviewed real-world calibration;
  S4 owns feedback and Precision@10 rather than automatic self-training.
- Original resume storage, schedules, and external notifications are not implemented.
- Market insights are shared market-level projections rather than private workspace projections.
- The inclusive taxonomy is a curated starter set (expanded 2026-09-11 with ~34 sales/business-
  development terms, migration `V28`), with optional versioned ESCO skill/occupation releases imported
  by `escoTaxonomyImportJob`; users can add workspace-private skills and roles; expanding or governing
  the shared seed remains deliberate work. Matching itself is exact-phrase (`PhraseAutomaton`), not
  fuzzy/semantic, so recall is capped by whatever is literally in the catalog regardless of size — see
  the queued semantic-extraction candidate in
  [NEXT_MILESTONES.md](NEXT_MILESTONES.md#semantic-skill-extraction-candidate-not-yet-selected).
- Title confidence orders deterministic evidence sources and is not a probability or claim that a
  suggested title is factually correct.
- React currently exposes only upload-derived role suggestions and a plain-text sector field; it does
  not use the existing live role-catalogue search or a normalized sector selector.
- Dashboard ordering is score-descending over at most 25 eligible workspace sightings, but the first
  row is featured without a minimum recommendation-quality boundary. Treat this as relative ordering,
  not verified strong relevance.
- Resume skill review groups catalogue matches by their existing category and evidence order, reveals
  long groups incrementally, rejects short lowercase taxonomy fragments, and keeps preferred sectors
  optional. React setup now preserves all normalized market rows. Search countries are selected by
  name and reset only that row's city/region default when changed; unsupported legacy codes must be
  reviewed rather than silently mapped to another country. Current location remains separate and its
  browser-derived estimate is always labelled and editable.
- Java repositories explicitly reference SQL resource paths. SQL is correctly externalized, but
  those string paths are runtime-checked and should gain a typed, startup-validated registry.

## 12. Handoff rule

The feature milestones through M3.2 remain verified implementation history. D1 now supersedes the
old personal/learning requirement boundary. The startup delivery index is:

```text
D1 Startup requirements and architecture — COMPLETE
D2 V1 engineering standards — COMPLETE
D3 Free demo deployment — COMPLETE (2026-09-10)
S0.1 Discovery execution safety — COMPLETE (2026-09-08)
Assisted multi-market onboarding follow-up — COMPLETE (2026-09-08)
S0.2 Onboarding correctness — SELECTED, IMPLEMENTATION VERIFIED, ACCEPTANCE OPEN
S0.3 Cross-role ranking correctness — COMPLETE (2026-09-11)
S1 Authenticated account ownership and RBAC — NOT STARTED
S2 Product run commands, safe concurrency/recovery and live progress — NOT STARTED
S3 Shared ingestion and provider budgets — NOT STARTED
S4 Job Explorer and reviewed ranking quality — NOT STARTED
S5 Résumé object lifecycle and privacy workflows — NOT STARTED
S6 Production platform gate — NOT STARTED
```

Former M3.3/M3.4 scope is consolidated under S4. M2.8 remains an unstarted historical-track option
and does not override S0–S6. Do not combine the
startup modules or silently advance. At each boundary, finish tests, evidence, documentation, and a
conventional commit, then obtain explicit user direction before starting another module.

The implementation baseline is M3.2 plus S0.1 and the completed assisted multi-market onboarding
follow-up; the product baseline is D1 and the engineering baseline is D2. The 2026-09-09 additions
extend the owner-selected S0.2 checkpoint with live role/sector assistance and the premium
résumé-review quality gate, and S0.3 with the recommendation qualification contract. Do not combine
S0.2 with S0.3, S1–S6, M2.8, or a
microservice split without an explicit checkpoint decision.
