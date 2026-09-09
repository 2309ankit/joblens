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
Next shipping milestone: select exactly one of the still-queued checkpoints; none is currently selected
Java: 21
Spring Boot: 4.1.1 (deliberate recorded deviation from the original 3.x request)
Spring Batch: 6
Database: PostgreSQL 17
Latest Flyway migration: V25
Latest full test: 130 Java tests plus 8 React tests, 0 failures, 0 errors, 0 skipped
Latest focused check: two-market React/API activation, duplicate/blank validation, browser location policy, and responsive layout pass
Local runtime: Docker app running with index-DdhNRvf8.js and index-BG1Cp8qN.css; PostgreSQL healthy; /actuator/health reports UP
Owner-reported next items: role/sector suggestion regression, recommendation-board semantics, and premium résumé intelligence UX; documented but no checkpoint selected
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

## 2. Assisted multi-market onboarding follow-up — complete

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
accuracy contract. Select exactly one queued checkpoint before new implementation.

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

Status: **OPEN and not investigated**, recorded from a user-supplied Senior AI Engineer résumé on
2026-09-03. The supplied résumé contains personal contact and profile information; those values are
deliberately not copied into this repository. The minimum redacted evidence needed to reproduce the
problem is recorded below.

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

Expected product boundary to verify later: contact, social, and blog labels or URL hosts must not
become job-title suggestions; identical canonical suggestions should not be repeated; evidence source
labels should correspond to the actual résumé section; and explicit recent experience should be
eligible to suggest more than one plausible direction without silently selecting it as search intent.
Do not change extraction heuristics until the stored suggestion/evidence rows and section parsing for
this redacted fixture have been inspected.

#### UX-M3-003 — Preferred sectors need a selectable catalogue control

Status: **OPEN product/UX gap; reported again on 2026-09-09; not designed or implemented**.

The setup form currently makes preferred sectors difficult to enter consistently. The requested
direction is a searchable dropdown or multi-select backed by normalized sector records, while keeping
preferred sectors optional. The handoff must not assume whether this reuses an existing taxonomy,
adds a small curated sector catalogue, or permits workspace-private values; that data-model decision
must be made before implementation. Preserve selected values across profile versions and ensure the
same normalized values drive query generation and the optional sector ranking dimension.

Inspection on 2026-09-09 confirms that the React page still renders preferred sectors as one plain
text input. It has no suggestion source, normalized selectable values, token/chip treatment, keyboard
listbox, or empty/loading/error state. This is separate from the completed multi-market fix.

#### BUG-M3-004 — City/region is mandatory even when country is selected

Status: **OPEN and not investigated**.

Observed behavior: setup requires a city/region value even when the user has already selected a
supported country. The user expects country-only searches to be valid and city/region to narrow the
market only when supplied. Before changing validation, verify each provider's behavior for a blank or
country-level location and how `workspace_search_target`, query generation, source profiles, scoring,
and portal links represent a country-only target. Do not silently manufacture a city or make the
stored ISO country code user-editable.

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

Status: **OPEN and not investigated**. This is a ranking-quality query, not evidence that a specific
weight or matcher is already known to be wrong.

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

Status: **OPEN and inspected on 2026-09-09; not implemented**. Assign to S0.2 if selected.

Owner report: role and sector fields have stopped offering useful suggestions. Code inspection
confirms two distinct gaps:

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

Status: **OWNER REQUIREMENT recorded on 2026-09-09; not designed or implemented**. Assign to S0.2
only if explicitly selected.

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
- The inclusive taxonomy is a curated starter set, with optional versioned ESCO skill/occupation
  releases imported by `escoTaxonomyImportJob`; users can add
  workspace-private skills and roles; expanding or governing the shared seed remains deliberate work.
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
S0.1 Discovery execution safety — COMPLETE (2026-09-08)
Assisted multi-market onboarding follow-up — COMPLETE (2026-09-08)
S0.2 Onboarding correctness — QUEUED, NOT STARTED
S0.3 Cross-role ranking correctness — QUEUED, NOT STARTED
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
extend S0.2 with live role/sector assistance and the premium résumé-review quality gate, and S0.3 with
the recommendation qualification contract; they do not mark either checkpoint as selected. Select
exactly one next checkpoint before implementation. Do not combine S0.2, S0.3, S1–S6, M2.8, or a
microservice split without an explicit checkpoint decision.
