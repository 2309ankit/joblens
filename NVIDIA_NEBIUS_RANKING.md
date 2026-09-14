# NVIDIA-on-Nebius Ranking — AI-RANK-01

Status: implementation deployed behind a default-off flag; live Nebius validation pending
(2026-09-15)

## Decision

JobLens will use an NVIDIA open-source model served through Nebius Token Factory as the primary
final job-fit score when a valid result is available. The existing `universal-v2` deterministic
score remains mandatory: it creates the bounded shortlist, provides explainable baseline evidence,
and is the automatic fallback for disabled AI ranking, cache misses, budget exhaustion, timeouts,
provider failures, or invalid model output.

The local ONNX résumé skill experiment is independent of this decision. It is neither the final
ranker nor removed by AI-RANK-01.

## First-slice boundaries

* Add one `nvidiaScoringStep` after deterministic scoring in `jobIntelligenceJob` and `findJobsJob`.
* Keep the feature disabled by default. Enabling it requires `NEBIUS_API_KEY` and an explicit
  `NEBIUS_MODEL`; no model ID is hard-coded because Token Factory availability changes.
* Score at most a configured number of deterministically shortlisted jobs per run.
* Make model calls outside a database transaction. Persist each success or failure separately.
* Cache a successful score by candidate profile version, normalized job content hash, model ID, and
  prompt version.
* Accept only bounded JSON containing a 0–100 score, confidence, recommendation decision, concise
  summary, and reasons. Invalid output is a visible failed attempt and uses the deterministic score.
* Send structured candidate preferences and job facts, not the original résumé document. Treat job
  descriptions as untrusted text in the model instruction.
* Prefer a current cached NVIDIA score in the dashboard read model; otherwise retain the existing
  deterministic score and recommendation decision.

## Operating controls

The first slice has per-run and persisted daily call limits, a request timeout, an output-token cap,
and no automatic HTTP retry. This bounds cost and avoids repeated paid calls in the current
single-worker deployment. A concurrency-safe shared quota reservation is still required before
enabling multiple ranking workers. The provider model
identifier, prompt version, token usage, confidence, and sanitized failure are retained for audit.

## Activate on Render

The deployed application starts normally without NVIDIA credentials and continues to use
`universal-v2`. To activate the provider:

1. In [Nebius Token Factory](https://tokenfactory.nebius.com), open **API keys**, create a key, and
   save it when shown. Do not paste it into Git, documentation, application logs, frontend code, or a
   chat message.
2. Select an NVIDIA open-source chat model from the current Token Factory catalogue. The catalogue is
   the source of truth; use its exact model ID as `NEBIUS_MODEL`.
3. In Render, open the `joblens-demo` service, choose **Environment**, and add these runtime values:

   ```env
   NEBIUS_API_KEY=<secret API key>
   NEBIUS_MODEL=<exact current NVIDIA model ID>
   JOBLENS_NVIDIA_RANKING_ENABLED=true
   NEBIUS_MAX_JOBS_PER_RUN=1
   NEBIUS_MAX_CALLS_PER_DAY=5
   ```

4. Save the environment so Render redeploys. Run Find Jobs once using synthetic or redacted profile
   data and verify one NVIDIA score before increasing either limit.

The API key is required for live scoring. `NEBIUS_MODEL` is also required because JobLens
deliberately does not pin a catalogue entry that Nebius may later rename or retire. If any required
value is absent, leave `JOBLENS_NVIDIA_RANKING_ENABLED=false`; enabling with missing values is a
configuration error. Rotating a key only requires replacing the Render secret and redeploying.

## Acceptance evidence

The local slice is complete when tests prove: disabled mode makes no provider call; valid JSON is
persisted and becomes the displayed score; the same cache identity is not charged twice; malformed
JSON and provider failures are recorded without replacing the deterministic score; and call limits
stop additional requests. A live Nebius verification remains separate and requires user-supplied
credentials plus a currently available NVIDIA model selected from Token Factory's model catalogue.
The deployed code at commit `65604e1` was healthy on Render, but this is not live-model acceptance:
the flag remained off and no user key was available.
