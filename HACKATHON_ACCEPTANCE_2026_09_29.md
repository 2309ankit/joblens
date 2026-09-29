# HACKATHON-01 — live evidence, September 29, 2026 (Singapore)

Scope: verify the existing demo and prepare submission materials. No application code, runtime
flags or provider budgets changed. Only the previously designated synthetic workspace was used.

## Fresh Find Jobs run

- Submitted one POST with business date `2026-09-29`; no retries or duplicate POSTs.
- HTTP 200 after 92.094 seconds: run ID **50**, status and outcome **COMPLETED**.
- Persisted start: `2026-09-28T16:42:43.898001Z`; completion:
  `2026-09-28T16:44:13.326795Z` (September 29 in Singapore).
- Adzuna Singapore: one page, 20 records, 10 new, 10 changed; 20 normalized/sighted/scored.
  Planning provenance **LLM_NEBIUS** with a rationale explaining why the existing specific query
  was retained based on the previous 20-result run.
- Jooble Singapore: one page, 20 records, 14 new, 6 changed; 20 normalized/sighted/scored.
  Planning provenance **DETERMINISTIC**.
- Neither source reported a failure reason. No source returned zero records.
- Before the run, the dashboard returned NVIDIA-scored job IDs 2390 and 2400. Afterward, job 3418
  also appeared with score 78 and `scoring_source=NVIDIA_NEBIUS`.

This verifies one successful deployed discovery-to-scoring run and displayed model-planning
provenance. It does not prove the query changed, ranking quality improved, or every source/job used
AI. Runtime model ID and exact remaining daily budgets were not independently queried this session.
The September 18 acceptance separately records the model ID and cache-reuse evidence.

## Limits and outstanding checks

- Source inspection found a UI gap: `frontend/src/main.jsx` does not consume `scoring_source`,
  `score_summary`, `queryPlanningSource`, or `queryPlanningRationale`. The live evidence above is
  from the dashboard API, not proof that React displays those labels/explanations. Submission
  materials must distinguish this until the display is implemented and browser-verified.
  Follow-up: the owner approved HACKATHON-UI-01; these fields are now rendered and locally
  browser-verified. Deployment remains pending. See `HACKATHON_AI_EVIDENCE_UI.md`.
- The HTTP request remained synchronous for 92 seconds. Shorter clients can still time out while
  durable work proceeds; the broader asynchronous-run milestone is not implemented here.
- This check starts from the established synthetic profile; it is not a fresh résumé-upload/browser
  rehearsal or a load test.
- UptimeRobot monitor 804111686 was created with a five-minute interval. Its initial own probe
  result has not yet been verified; the old GitHub workflow remains enabled.
- Apache 2.0 selected by the owner and added locally; publication/detection on GitHub is pending.
  Final video, Devpost field completion and final submission are still pending.

## Verification artifacts

Sanitized observations above were derived from the launch response and workspace dashboard API.
Temporary response files are under `/tmp/joblens-hackathon-*`; they are not repository artifacts.
No credentials or personal résumé content are included in this record.
