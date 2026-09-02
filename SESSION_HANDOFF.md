# JobLens Session Handoff

Use this file to resume work quickly. Historical implementation evidence remains in
[BUILD_PROGRESS.md](BUILD_PROGRESS.md), the operator guide is [README.md](README.md), and selectable
future work is indexed in [NEXT_MILESTONES.md](NEXT_MILESTONES.md).

## 1. Resume checkpoint

```text
Repository: /Users/ankitkumar/IdeaProjects/joblens
Branch: main
Implementation baseline: M3.2 Role-Aware Ranking
M3.2 implementation commit: 2885c84 (feat(ranking): add role-aware calibration)
Java: 21
Spring Boot: 4.1.1 (deliberate recorded deviation from the original 3.x request)
Spring Batch: 6
Database: PostgreSQL 17
Latest Flyway migration: V23
Latest full test: 111 tests, 0 failures, 0 errors, 0 skipped
Latest focused check: fresh PostgreSQL 17 through V23; role-aware ranking, intelligence, and Find-jobs integration suites pass
Local runtime: Docker app running; PostgreSQL healthy; /actuator/health reports UP
```

Before making changes:

```bash
cd /Users/ankitkumar/IdeaProjects/joblens
git status --short
docker compose ps
```

Read [AGENTS.md](AGENTS.md) and [SYSTEM_DESIGN.md](SYSTEM_DESIGN.md), then select exactly one milestone from
[NEXT_MILESTONES.md](NEXT_MILESTONES.md). Do not infer or combine milestones.

## 2. M3.2 completed slice — exact handoff

### Product and architecture decision

M3.2 does not divide professions into "supported" roles and a weaker generic algorithm. Every one of
the candidate's one to three ordered target roles is evaluated by the same deterministic,
versioned `universal-v1` policy. A mapped role also receives an additive, versioned calibration
overlay containing curated title and skill evidence. An unmapped catalogue or workspace-private role
still receives the full universal policy using its selected title, aliases, confirmed candidate
skills, sectors, seniority, markets, work arrangement, employment type, salary availability, and
freshness.

The overlays are transparent starter rules, not a claim of measured ranking accuracy. M3.4 must use
user-reviewed Fit/Maybe/Not-fit examples and sample-qualified Precision@10 before changing weights or
claiming improvement. There is no LLM, automatic self-training, forced role classification, or hidden
job rejection.

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

M3.3 Job Explorer has not started. It owns backend SQL/API filtering by country/search market, source,
freshness, work mode, and saved state; Recommended/Newest sorting; country grouping; and stable
20-row keyset **Load more** pagination that preserves filter/sort state. These operations should be
performed in PostgreSQL, with the browser limited to rendering and interaction.

M3.4 has not started. It owns workspace-private Fit/Maybe/Not-fit feedback, optional reason codes,
auditability, reviewed fixture samples, false-positive analysis, and Precision@10 reporting by role
pack, market, and scoring version. M2.8 Typed SQL Resource Registry remains deferred while M3 is
active. Do not start any of these without an explicit user checkpoint.

### Open observed bugs and UX gaps

#### BUG-M3-001 — AI Engineer returns no integrated results

Status: **OPEN and not investigated**, recorded from user observation on 2026-09-03. Do not claim a
cause or a fix without reproducing it and collecting evidence.

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

Required evidence for a future diagnosis, without assuming the fault is ranking or Adzuna:

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

Status: **OPEN product/UX gap; not designed or implemented**.

The setup form currently makes preferred sectors difficult to enter consistently. The requested
direction is a searchable dropdown or multi-select backed by normalized sector records, while keeping
preferred sectors optional. The handoff must not assume whether this reuses an existing taxonomy,
adds a small curated sector catalogue, or permits workspace-private values; that data-model decision
must be made before implementation. Preserve selected values across profile versions and ensure the
same normalized values drive query generation and the optional sector ranking dimension.

#### BUG-M3-004 — City/region is mandatory even when country is selected

Status: **OPEN and not investigated**.

Observed behavior: setup requires a city/region value even when the user has already selected a
supported country. The user expects country-only searches to be valid and city/region to narrow the
market only when supplied. Before changing validation, verify each provider's behavior for a blank or
country-level location and how `workspace_search_target`, query generation, source profiles, scoring,
and portal links represent a country-only target. Do not silently manufacture a city or make the
stored ISO country code user-editable.

#### BUG-M3-005 — Find Jobs leaks a raw Spring Batch already-running error

Status: **OPEN and not investigated**.

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

## 3. Product flow that works now

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

## 4. Current architecture

JobLens remains a batch-first modular monolith: one Spring Boot application, one PostgreSQL database,
one deployable image.

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
| `searchProfileImportJob` | Original CSV/Spring Batch learning workflow |
| `jobDiscoveryJob` | Operator launch of discovery only |
| `jobIntelligenceJob` | Operator launch of normalization through scoring |
| `applicationFollowUpJob` | Candidate-scoped deterministic reminders |
| `weeklyMarketInsightJob` | Shared weekly market aggregates |

Automatic Batch startup remains disabled. Jobs use meaningful identifying parameters, persisted
ExecutionContext checkpoints, observable failures, restarts, and idempotent writes.

## 5. Job sources

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

## 6. Data and processing decisions

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

## 7. Main inspection points

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
GET  /api/batch/find-jobs/runs/{jobExecutionId}
POST /api/batch/find-jobs/runs/{jobExecutionId}/restart
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

## 8. Code map

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
  db/migration/   Flyway V1-V23
  sql/            externalized SQL grouped by feature
  templates/      setup, dashboard, applications
```

## 9. Verification commands

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

## 10. Known limitations

- Anonymous cookie workspaces have no account recovery or cross-device synchronization.
- Jooble needs a regional key and its provider quota must be monitored.
- Jooble runs only for the market matching `JOOBLE_COUNTRY_CODE`; Adzuna provides the other configured
  broad-market searches.
- Greenhouse enrichment activates only when a legitimate source exposes a direct official board URL.
- Lever enrichment activates only for a direct global `jobs.lever.co` URL; tracking redirects and the
  separate EU host are deliberately unsupported.
- Discovery is sequential and intentionally unpartitioned at current personal scale.
- Fuzzy thresholds and the initial role-overlay signals still need reviewed real-world calibration;
  M3.4 owns feedback and Precision@10 rather than automatic self-training.
- Original resume storage, schedules, and external notifications are not implemented.
- Market insights are shared market-level projections rather than private workspace projections.
- The inclusive taxonomy is a curated starter set, with optional versioned ESCO skill/occupation
  releases imported by `escoTaxonomyImportJob`; users can add
  workspace-private skills and roles; expanding or governing the shared seed remains deliberate work.
- Title confidence orders deterministic evidence sources and is not a probability or claim that a
  suggested title is factually correct.
- Resume skill review groups catalogue matches by their existing category and evidence order, reveals
  long groups incrementally, rejects short lowercase taxonomy fragments, and keeps preferred sectors
  optional. Search countries are selected by name and reset the city/region default when changed;
  unsupported legacy codes must be reviewed rather than silently mapped to another country.
- Java repositories explicitly reference SQL resource paths. SQL is correctly externalized, but
  those string paths are runtime-checked and should gain a typed, startup-validated registry.

## 11. Handoff rule

M0 Jooble live acceptance, M0.5 Portal Search Hub, M0.6 Smart Portal Query Planner, M1 source
health/run observability, M2 Lever, M2.5 normalized multi-market preferences, M2.6 Inclusive
Profile Intelligence, M2.7 Explainable ATS Readiness Advisor, and D0 System Design Baseline are
complete. The selected order is:

```text
M2.7 Explainable ATS Readiness Advisor — COMPLETE
D0 System Design Baseline — COMPLETE
M3 General Role Intent, Job Explorer, and Ranking Calibration
  → approved and started on 2026-09-02; M3.1 intent and generated queries is complete
  → M3.2 role-aware ranking is complete, including the Backend Engineering overlay
  → M3.3 Job Explorer awaits the next explicit checkpoint decision
M2.8 Typed SQL Resource Registry
  → deferred while M3 is active
```

Do not combine these modules and do not silently advance from one to another. At each boundary,
finish tests, evidence, documentation, and a conventional commit, then explicitly ask the user before
starting the next module. React migration remains a later, separate presentation-layer decision.

M3.2 is verified. Do not start M3.3 or M2.8 without the next explicit checkpoint decision.
