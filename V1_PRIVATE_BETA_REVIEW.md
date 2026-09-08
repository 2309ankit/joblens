# JobLens V1 Private-Beta Review

Status: **review required; not approved for external release** (2026-09-08).

The appropriate corporate term for the planned early release is an **internal alpha** while access is
anonymous, and an **authenticated private beta** (or design-partner pilot) only after G1 is met. It is
not appropriate to call the current state a public V1 launch.

## Verified in this checkpoint

- React covers dashboard, assisted setup, and application tracking in the packaged Spring Boot app.
- Setup reduces manual input to résumé upload, editable suggested skills/roles, one market, and an
  explicit activation decision. Readiness warnings remain explicit acknowledgements.
- Applications and follow-ups remain workspace-owned; allowed lifecycle transitions are supplied by
  the server.
- S0.1 now provides workspace-safe query-to-score diagnostics, one-process duplicate admission,
  product run IDs/statuses, and recoverable orphaned stale runs without framework-detail leakage.
- `./mvnw clean test` passed on 2026-09-08: 129 Java tests and 3 React tests, with fresh PostgreSQL
  17 Testcontainers and Flyway through V25. Formatting and diff checks passed.

## Blocking decisions and work before an authenticated private beta (G1)

| Area | Required outcome | Review decision needed |
| --- | --- | --- |
| Identity and access | Real accounts, secure sessions, authorization, recovery, and workspace migration | Identity provider and account-linking model |
| Resume/privacy | Encrypted/scanned storage, retention, export and deletion | Storage vendor, retention and privacy policy |
| Run control | Distributed admission/leases, cancellation, and durable live progress beyond the verified single-process S0.1 guard | Product run state and provider budget policy |
| Delivery platform | CI/CD, staging, managed PostgreSQL, secrets, backups and restore rehearsal | Cloud, region, deployment tooling and operating owner |
| Operations | Health, structured/redacted logs, metrics, alerts, incident/support runbooks | SLOs, alert ownership and support coverage |
| Security | Threat model, dependency/SBOM scanning, security headers, rate limiting and testing | Security owner and acceptance criteria |

## Public-launch gates (G2), after private-beta evidence

- Load and security testing against agreed SLOs.
- Provider contracts, quotas, cost controls, and shared-ingestion evidence.
- Measured ranking quality with reviewed examples and support/admin workflows.
- Real-time progress, deletion/export workflows, and on-call/incident rehearsal.

## Decisions for product-owner review

1. Target release label: internal alpha now, or authenticated private beta after G1.
2. Pilot cohort, invite criteria, support channel, and success metrics.
3. Cloud/region, managed PostgreSQL, deployment tool, CI/CD owner, and rollback strategy.
4. Resume retention, deletion/export policy, and privacy/legal review owner.
5. Provider budget, market launch subset, and commercial/API-contract approval.

No deployment vendor or hosting target has been selected in this repository. That is deliberately a
decision item, not an implicit implementation choice.
