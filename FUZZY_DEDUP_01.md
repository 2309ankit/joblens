# FUZZY-DEDUP-01 — indexed, restartable fuzzy comparison

Owner selected this fix together with AI-RANK-01 acceptance and QUERY-PLAN-01 deployment on
2026-09-15. Preserve fuzzy-v1 scores and candidate semantics; no provider or user data is deleted.

## Design and acceptance

Prepare normalized text/token/trigram sets once per job, then use inverted title-token, company,
and title-trigram indexes to enumerate candidate pairs. Trigram overlap counts must preserve the
existing rounded Dice >= 70 rule. Keep exact-cluster suppression and existing scoring weights.
The corpus remains global so cross-source suggestions do not silently lose recall.

Run CPU comparison outside a database transaction. Reconcile groups of 25 left jobs in a short
transaction and checkpoint the last ID. Restart replays at most one committed chunk after a crash
between its write and checkpoint; idempotent upserts preserve timestamps. Cleanup is scoped to that
left-job chunk, never an incomplete global desired-pair list. Concurrent executions serialize group
writes using a row lock. New executions reconcile changed content and remove stale similarities.

Acceptance: exhaustive-v1 parity across diverse fixtures; no missing rounded-trigram candidates;
PostgreSQL idempotence, stale removal, failure/restart and partial-progress tests; 5,000-job synthetic
candidate-index check completes under 10 seconds; full Maven/frontend and formatting checks pass;
deployed Find Jobs reaches NVIDIA scoring without the previous idle-transaction timeout.

Memory remains proportional to the corpus and token sets. Truly dense same-company/title corpora
can still produce quadratic output; this change removes unconditional pair scans and repeated text
processing, not that inherent worst case. Shared incremental ingestion and production-scale
partitioning remain separate milestones. No migration is required. Rollback deploys the preceding
artifact; the job_similarity schema and fuzzy-v1 data remain compatible.

## Local verification — 2026-09-16 (Singapore)

`./mvnw clean test`: 212 Java tests and 21 frontend tests passed, no failures/errors/skips.
PostgreSQL 17/Flyway V35 verified from clean Testcontainers. Spotless and diff checks passed.
The two index tests, including the 5,000-job sparse fixture, took 0.643 seconds in the focused run.
The full suite took 42.403 seconds. Live deployment/acceptance is still to be recorded separately.
