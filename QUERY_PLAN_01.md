# Agentic query planning — QUERY-PLAN-01

Status: implementation on PR #1 behind a default-off flag; merge/deploy and live validation pending
(2026-09-16)

## Decision

An NVIDIA open-source model served through Nebius Token Factory may optionally refine a search
profile's provider query text and page budget for one Find Jobs run, using that profile's own most
recent result outcome. The existing deterministic `ProviderQueryPlanner` output (persisted at profile
activation time in `search_profile.keywords`) remains authoritative and is the automatic fallback for
disabled planning, cache/budget exhaustion, timeouts, provider failures, or invalid model output.

This is a separate feature from `AI-RANK-01` (`NVIDIA_NEBIUS_RANKING.md`), which re-scores already-
discovered jobs. QUERY-PLAN-01 instead participates in deciding *what to search for* before discovery
runs — the more "agentic" half of using an LLM in the job-search pipeline.

## First-slice boundaries

* Add one `agenticQueryPlanningStep` before `jobDiscoveryStep` in `findJobsJob` only (not
  `jobIntelligenceJob`, which never runs discovery).
* Keep the feature disabled by default, independently of AI-RANK-01. Enabling it requires
  `NEBIUS_API_KEY` and an explicit `NEBIUS_QUERY_PLANNING_MODEL`.
* Plan at most a configured number of a workspace's active search profiles per run
  (`NEBIUS_QUERY_PLANNING_MAX_PROFILES_PER_RUN`).
* The model may only refine `queryText` and `maxPages` for a search profile the user already
  configured through onboarding. It cannot create, remove, or redirect a search to a different
  provider or market — the candidate list of profiles it is given is fixed, and its response is
  validated against exactly that list.
* `maxPages` may be raised above a profile's own configured value, but never above a fixed hard
  ceiling (`NEBIUS_QUERY_PLANNING_MAX_PAGES_CEILING`).
* Treat the profile's role/skills/history as untrusted data in the model instruction, matching
  AI-RANK-01's isolation approach.
* Accept only bounded JSON containing `queryText`, `maxPages`, and a one-sentence `rationale`.
  Invalid output is a visible failed attempt; the deterministic query remains in effect for that
  profile.
* A per-run decision is not cross-run cached (unlike AI-RANK-01's score cache) — it is inherently
  scoped to one job execution, since it should reflect the latest available run history each time.
* `JobDiscoveryTasklet` looks up an applied decision for `(jobExecutionId, searchProfileId)` right
  before issuing the provider request; when present it overrides that one request's keywords/pages
  without mutating the persisted `search_profile` row. The existing query-broadening safety net still
  runs unchanged on top of whichever keywords are in effect.

## Operating controls

Independent per-run and persisted daily call limits, a request timeout, and an output-token cap,
mirroring AI-RANK-01's operating controls exactly (`LlmQueryPlanningProperties`). No automatic HTTP
retry. The provider model identifier, prompt version, and sanitized failure are retained for audit in
`query_plan_decision`/`query_plan_attempt` (migration `V35`).

## Known risk, noted not blocking

Update (2026-09-16): FUZZY-DEDUP-01 is implemented and locally verified in the same PR; verify the
deployed pipeline before enabling paid query-planning acceptance. The paragraph below is historical.

`FuzzyDuplicateDetectionTasklet` (a step later in the same `findJobsJob` pipeline) has a documented,
unrelated O(n²) global-table bug that has already crashed live Find Jobs runs — see
`NEXT_MILESTONES.md`. Do not enable this feature's live acceptance test on the shared Render demo
until that bug is fixed or the job table is small enough that a run reliably completes; adding another
live external call to the same pipeline compounds an already-observed failure mode. Building and
merging this feature disabled-by-default carries none of that risk.

## Activate on Render

The deployed application starts normally without these credentials and continues to use the
deterministic query planner unchanged. To activate:

1. In [Nebius Token Factory](https://tokenfactory.nebius.com), reuse the existing `NEBIUS_API_KEY` (or
   create a separate one) — same account as AI-RANK-01.
2. Select an NVIDIA open-source chat model from the current Token Factory catalogue for
   `NEBIUS_QUERY_PLANNING_MODEL`. It can be the same model used for scoring, or a different one.
3. In Render, open the `joblens-demo` service, choose **Environment**, and add:

   ```env
   JOBLENS_QUERY_PLANNING_ENABLED=true
   NEBIUS_QUERY_PLANNING_MODEL=<exact current NVIDIA model ID>
   NEBIUS_QUERY_PLANNING_MAX_PROFILES_PER_RUN=5
   NEBIUS_QUERY_PLANNING_MAX_CALLS_PER_DAY=20
   NEBIUS_QUERY_PLANNING_MAX_PAGES_CEILING=5
   ```

4. Save the environment so Render redeploys. Run Find Jobs once using synthetic or redacted profile
   data and verify one applied `query_plan_decision` row before increasing any limit.

## Acceptance evidence

The local slice is complete when tests prove: disabled mode makes no provider call
(`AgenticQueryPlanningTaskletTests`); a valid plan is persisted and consumed by
`JobDiscoveryTasklet` in place of the stored profile (`JobDiscoveryTaskletTests`); one profile's
provider failure does not block another profile's plan in the same run; malformed JSON, an
out-of-range `maxPages`, and a null-content reasoning-budget exhaustion are all rejected
(`QueryPlanningClientTests`); and the daily call limit stops additional requests. A live Nebius
verification remains separate and requires user-supplied credentials, a currently available NVIDIA
model, and a Find Jobs run that completes cleanly (see the known risk above).
