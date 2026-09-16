# JobLens Codebase Index

Purpose: fast repository navigation for Codex sessions and human reviewers. This is a source map,
not a substitute for reading the authoritative requirement, design, progress, and handoff documents.

## Freshness

- Indexed on: 2026-09-16
- Baseline branch: `feature/query-plan-01-agentic-search`
- Baseline commit: `e51cab2` plus the FUZZY-DEDUP-01 changes in this commit
- Indexed scope: Git-visible project files returned by `rg --files`; ignored build output, dependency
  directories, Git internals, secrets, and the user-owned untracked `.neon` path are excluded.
- Before relying on this map, compare `git branch --show-current` and `git rev-parse --short HEAD` to
  the values above. If the structure changed, use `rg --files` and `rg -n` as the source of truth and
  update this index only when module boundaries or important entry points changed.

## Read-first documents

| File | Authority |
| --- | --- |
| `AGENTS.md` | Repository-specific agent rules and verified-state summary |
| `PRODUCT_REQUIREMENTS.md` | JobLens V1 product and launch contract |
| `SYSTEM_DESIGN.md` | Architecture, data ownership, and runtime boundaries |
| `ENGINEERING_STANDARDS.md` | Mandatory requirement, implementation, test, review, and release gates |
| `SESSION_HANDOFF.md` | Current branch, deployment state, blockers, and exact resume point |
| `NEXT_MILESTONES.md` | Selectable work; do not combine milestones without owner approval |
| `BUILD_PROGRESS.md` | Historical evidence and verified progress only |
| `README.md` | Local build and operator runbook |

Feature-specific decisions currently include `NVIDIA_NEBIUS_RANKING.md`, `QUERY_PLAN_01.md`,
`S0_2_ONBOARDING_CORRECTNESS.md`, `S0_4_SEMANTIC_SKILL_EXTRACTION.md`,
`D3_FREE_DEMO_DEPLOYMENT.md`, `ATS_READINESS.md`, and `V1_PRIVATE_BETA_REVIEW.md`.

## Repository layout

| Path | Contents |
| --- | --- |
| `src/main/java/com/ankit/joblens/` | Spring Boot application and modular-monolith Java code |
| `src/main/resources/application.properties` | Runtime configuration and environment-variable bindings |
| `src/main/resources/db/migration/` | Ordered Flyway migrations; PostgreSQL is authoritative |
| `src/main/resources/sql/` | External SQL grouped by owning module/use case |
| `src/test/java/com/ankit/joblens/` | Java unit and PostgreSQL/Testcontainers integration tests |
| `src/test/resources/` | Test fixtures and test configuration |
| `frontend/src/` | React SPA source and Vitest tests |
| `frontend/` | Vite/package configuration and browser assets |
| `data/` | Checked-in safe sample/import data |
| `.github/workflows/deploy.yml` | Test-gated deployment from `main` to Render |
| `Dockerfile`, `compose.yaml`, `render.yaml` | Container, local PostgreSQL, and Render definitions |
| `pom.xml`, `mvnw`, `.mvn/` | Java 21 / Spring Boot 4.1.1 Maven build |

## Java module map

All paths below are relative to `src/main/java/com/ankit/joblens/`.

| Module | Responsibility | Useful entry points |
| --- | --- | --- |
| `batchapi/` | REST launch/history/query endpoints and API exception mapping | `BatchExecutionController`, `FindJobsController`, `JobQueryController`, `ResumeProfileController` |
| `config/` | Cross-cutting web/OpenAPI configuration | `OpenApiConfiguration`, `DashboardAssetCacheControlFilter` |
| `dashboard/` | Workspace dashboard read model, SPA route forwarding, views, portal links | `DashboardApiController`, `DashboardController`, `PortalSearchQueryPlanner`, `JobViewService` |
| `discovery/` | Source adapters, raw landing, Find Jobs orchestration, source-run history, optional query planning | `FindJobsConfiguration`, `JobDiscoveryTasklet`, `DiscoveryPersistenceService`, `AdzunaJobSourceClient`, `JoobleJobSourceClient`, `GreenhouseJobSourceClient`, `LeverJobSourceClient` |
| `intelligence/` | Normalization, skills, exact/fuzzy duplicates, deterministic scoring, NVIDIA final scoring | `JobIntelligenceConfiguration`, `JobNormalizationProcessor`, `SkillExtractor`, `FuzzyDuplicateDetectionTasklet`, `JobScoreCalculator`, `NvidiaScoringTasklet` |
| `intelligence/embedding/` | Optional local ONNX semantic matching primitives | `OnnxTextEmbeddingModel`, `TextEmbeddingModel`, `CosineSimilarity` |
| `jdbc/` | Shared classpath SQL loader | `ClasspathSql` |
| `lifecycle/` | Applications, audited transitions, follow-up planning and batch generation | `ApplicationLifecycleService`, `ApplicationLifecycleRepository`, `ApplicationFollowUpTasklet` |
| `market/` | Weekly market insight batch aggregation | `WeeklyMarketInsightConfiguration`, `WeeklyMarketInsightRepository` |
| `onboarding/` | Resume parsing/readiness, draft activation, preferences, governed vocabularies, search-target planning | `OnboardingService`, `ProfileIntelligenceExtractor`, `ProviderQueryPlanner`, `EscoTaxonomyImportTasklet` |
| `searchprofile/` | CSV search-profile import, validation, rejection capture, upsert | `SearchProfileImportJobConfiguration`, `SearchProfileProcessor`, `SearchProfileJdbcWriter` |
| `workspace/` | Anonymous workspace resolution and candidate-profile ownership | `WorkspaceContext`, `WorkspaceCandidateProfileService`, `WorkspaceRepository` |

## Batch flow index

```text
findJobsJob
  agenticQueryPlanningStep (optional; deterministic fallback)
  -> jobDiscoveryStep
  -> jobNormalizationStep
  -> skillExtractionStep
  -> exactDuplicateDetectionStep
  -> fuzzyDuplicateDetectionStep
  -> scoringStep (mandatory universal-v2)
  -> nvidiaScoringStep (optional; deterministic fallback)

jobIntelligenceJob
  jobNormalizationStep
  -> skillExtractionStep
  -> exactDuplicateDetectionStep
  -> fuzzyDuplicateDetectionStep
  -> scoringStep
  -> nvidiaScoringStep
```

Other job definitions are owned by their modules: search-profile import in `searchprofile/`,
application follow-ups in `lifecycle/`, ESCO import in `onboarding/`, and weekly insights in `market/`.
Automatic execution on application startup must remain disabled.

## NVIDIA Nemotron on Nebius index

Final ranking (`AI-RANK-01`):

- Decision and operations: `NVIDIA_NEBIUS_RANKING.md`
- Environment bindings: `application.properties` under `joblens.ranking.nvidia.*`
- Guarded properties/step wiring: `NvidiaRankingProperties`, `NvidiaRankingConfiguration`
- Shortlist, cache, budgets, calls, and fallback: `NvidiaScoringTasklet`, `NvidiaScoringRepository`
- Prompt, bounded input, and strict output validation: `NebiusNvidiaScoringClient`
- Shared OpenAI-compatible transport: `NebiusChatCompletionClient`
- Pipeline placement: `FindJobsConfiguration`, `JobIntelligenceConfiguration`
- Persistence: `V34__create_nvidia_job_scoring.sql`
- Dashboard preference/fallback: `sql/dashboard/list-ranked-jobs.sql`
- Primary tests: `NebiusNvidiaScoringClientTests`, `NvidiaScoringTaskletTests`, and dashboard/pipeline integration tests

Agentic query planning (`QUERY-PLAN-01`, feature branch at index time):

- Decision and operations: `QUERY_PLAN_01.md`
- Environment bindings: `application.properties` under `joblens.query-planning.llm.*`
- Guarded configuration: `LlmQueryPlanningProperties`, `QueryPlanningConfiguration`
- Per-profile planning and validation: `AgenticQueryPlanningTasklet`, `QueryPlanningClient`, `QueryPlanningRepository`
- Consumption before provider calls: `JobDiscoveryTasklet`
- Persistence: `V35__create_agentic_query_planning.sql`
- Run-history provenance: `sql/find-jobs/list-run-sources.sql`
- Primary tests: `QueryPlanningClientTests`, `AgenticQueryPlanningTaskletTests`, and discovery integration tests

The two toggles and budgets are independent. Ranking has been configured on the Render service;
query planning was not merged to `main` or deployed at the index checkpoint. Read the top of
`SESSION_HANDOFF.md` and inspect current runtime evidence before making a newer claim.

## SQL and schema navigation

`src/main/resources/sql/` is grouped by capability: `dashboard`, `discovery`, `duplicate`,
`duplicate-query`, `find-jobs`, `intelligence`, `job-query`, `job-view`, `lifecycle`,
`lifecycle-query`, `onboarding`, `profile`, and `workspace`.

Find SQL consumers with both directions of search:

```bash
rg -n 'load\("sql/' src/main/java
rg -n 'the_table_or_column' src/main/resources/sql src/main/resources/db/migration src/main/java
```

The latest migration at this index checkpoint is V35 on the feature branch and V34 on `main`.
Never edit an already-applied migration to change deployed behavior; add a forward migration.

## Frontend index

- `frontend/src/main.jsx`: SPA bootstrap and dashboard surface.
- `frontend/src/Setup.jsx`: two-step resume/profile/search-market onboarding.
- `frontend/src/Applications.jsx`: application lifecycle and follow-ups.
- `frontend/src/api.js`: browser API adapter.
- `frontend/src/dashboardRecommendation.js`: Recommended vs Explore result grouping.
- `frontend/src/dashboardAutoRun.js`: dashboard run-state behavior.
- `frontend/src/navigation.js`, `messages.js`, `styles.css`: shared navigation, copy, and design system.
- Co-located `*.test.*` files are the focused Vitest suite.

## Common investigation commands

```bash
git status --short
git branch --show-current
git rev-parse --short HEAD
rg --files
rg -n 'symbol-or-contract' src frontend
rg -n 'property-name' src/main/resources src/main/java
./mvnw test
npm --prefix frontend test
git diff --check
```

Use narrower tests first for diagnosis, then the verification level required by
`ENGINEERING_STANDARDS.md`. Docker is required for PostgreSQL/Testcontainers behavior.

## Known high-risk navigation points

- `intelligence/FuzzyDuplicateDetectionTasklet`, `FuzzyCandidateIndex`, and
  `sql/duplicate/find-jobs.sql`: FUZZY-DEDUP-01 retains the global corpus, precomputes features,
  indexes candidates and checkpoints transactional 25-left-job writes. CPU work is nontransactional.
  Dense same-company/title corpora can still produce quadratic output; see `FUZZY_DEDUP_01.md`.
- NVIDIA/Nebius calls: never expose `NEBIUS_API_KEY`; send structured profile/job facts rather than
  original resume bytes; keep provider calls outside database transactions.
- `workspace/` plus every workspace-aware SQL query: preserve ownership predicates and negative
  authorization coverage.
- `onboarding/`: preserve draft-before-activation semantics and explicit user acknowledgement.
- `.github/workflows/deploy.yml`: a successful push to `main` already triggers the Render API deploy;
  do not start a duplicate manual deploy for the same commit.
- `.neon`: user-created, untracked, and outside this index; do not inspect, delete, or commit it
  without explicit owner direction.
