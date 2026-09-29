# HACKATHON-UI-01 — show existing AI evidence

## Requirement and scope

The owner approved fixing the dashboard before demo recording. The existing API exposes NVIDIA
score provenance and summaries, plus per-source query-planning provenance and rationale, but React
omitted them. A visitor must be able to distinguish model-assisted and rule-based results and read
the returned explanation without leaving the dashboard.

Acceptance: show evidence on the featured job, ranked job cards (including Explore results), and
each source card. Disclosures must work with a keyboard. Unknown sources must not be mislabeled as
AI or deterministic; missing explanations must not crash or invent evidence. Model text must render
as escaped text. An unchanged model-approved query must not be described as a rewritten query.

## Design and review

Two presentation components consume the existing workspace-scoped dashboard response. No API,
database, ranking, provider call, runtime toggle, budget, or source-selection change is needed.
Native details/summary controls keep longer explanations available without crowding the cards.
Explicit source equality gates AI explanations, so stale model fields cannot be attributed to a
rule-based result. Unknown sources receive an unavailable label. No inference-confidence percentage
is presented as the probability of getting a job.

Rollback: remove the component mounts, import and associated styles. No data migration or cleanup.

## Verification — September 29, 2026

- All 27 frontend tests passed, including six new rendering cases covering model/fallback/unknown
  provenance, missing explanations, unchanged-query wording and HTML escaping.
- Vite production build passed.
- Local production-bundle preview used the recorded synthetic-workspace response from live run 50;
  the preview made no provider calls. Chrome displayed NVIDIA labels on the featured and ranked
  cards, rule-based score labels on deterministic results, AI planning for Adzuna and rule-based
  planning for Jooble.
- Opened the score disclosure using keyboard controls; screenshot inspection confirmed readable
  summary text and a visible focus outline. Opened the query disclosure and verified the recorded
  rationale and unchanged-query clarification alongside the deterministic source card.
- This is local browser acceptance. Public deployment and mobile viewport acceptance remain open.
- Backend tests were not rerun: this change only renders existing fields and changes no backend
  code or contracts. No claim is made about new live model calls from these UI checks.
