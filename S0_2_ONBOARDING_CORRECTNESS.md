# S0.2 Onboarding Correctness

Status: **selected by the product owner on 2026-09-09; implementation and automated/browser
verification complete; owner fixture, parsing-p95, and assistive-technology acceptance remain open**.

This checkpoint is bounded to `BUG-M3-002`, `UX-M3-003`, `BUG-M3-004`,
`BUG-ONBOARDING-03`, and `UX-ONBOARDING-04`. S0.3 ranking semantics, authentication, résumé object
storage, external AI, analytics, new providers, and later launch infrastructure remain excluded.

## Requirement and actor

The actor is an anonymous JobLens V1 development-workspace user who uploads a résumé and reviews the
profile evidence, desired roles, optional sectors, and search markets before explicit activation.
The workspace cookie remains the ownership boundary. Suggestions are evidence, not confirmed intent.

The selected checkpoint must:

1. prevent contact labels, URLs, duplicate canonical roles, and unsafe short fragments from being
   presented as credible résumé directions;
2. retain plausible roles from the headline, professional summary, and dated experience with an
   evidence-source label that describes the actual section;
3. make skills, roles, and sectors searchable with bounded catalogue requests, keyboard operation,
   explicit workspace-private additions, deduplication, removal, and undo;
4. normalize optional sectors through a governed catalogue and preserve their canonical values in
   the existing profile/candidate compatibility arrays used by portal queries and score evidence;
5. allow a supported country to be activated without inventing a city or region; and
6. acknowledge file choice immediately, expose honest read/review states, progressively disclose
   extraction evidence, preserve explicit readiness acknowledgement, and retain review edits across
   refresh in the same browser session without retaining the original file.

## Design

### Taxonomy and persistence

Flyway V26 adds `sector_catalog` and `sector_alias`. The initial global taxonomy is
`joblens-sector-v1`; it is deliberately small, versioned, and independent of the ESCO occupation and
skill release. A typed custom entry creates a `WORKSPACE_PRIVATE` sector visible only to its owning
workspace. Existing `target_domains` arrays remain the compatibility projection, but activation now
resolves aliases and stores canonical names. This keeps existing provider, portal, and ranking reads
compatible while preventing free-text spelling variants from fragmenting future evidence.

The role and skill catalogue queries now search aliases as well as canonical labels. Alias input is
resolved to its canonical role or skill rather than creating a duplicate private value.

### Country-wide markets

V26 relaxes only the nonblank part of the `workspace_search_target.location` check; length and plain
text validation remain. A blank location means country-wide and is stored as blank, not as a
manufactured city. Adzuna omits its optional `where` parameter. Jooble's current official contract
requires a location string, so its adapter derives the country display name from the already
validated country source key. Outbound LinkedIn links use the same country display name.
Country/source validation, independent target checkpoints, ownership, and the ten-row limit are
unchanged. Contract references: [Adzuna API overview](https://developer.adzuna.com/overview) and
[Jooble REST API documentation](https://help.jooble.org/en/support/solutions/articles/60001448238-rest-api-documentation).

### Deterministic résumé evidence

Extractor version `esco-deterministic-v4` excludes contact/URL lines from both skill and role
evidence, exposes rejected short fragments only when they occur in explicit skill/tool sections,
recognizes the recorded hyphenated Front-end title aliases, and labels professional-summary roles as
`PROFESSIONAL_SUMMARY`. The one-row-per-profile-and-canonical-role database key continues to enforce
deduplication.

### UI and API

`GET /api/candidate-profile/sectors/catalog?query=` joins the existing workspace-safe skill and role
catalogue endpoints. React sends requests only after two typed characters and a 250 ms debounce. The
combobox/listbox contract includes visible focus, Arrow Up/Down, Enter, Escape, loading, no-match,
recoverable-error, custom-addition, limit, removal, and undo states.

The upload surface reports the selected name/type/size synchronously, then uses truthful combined
server stages rather than fabricated percentages. Evidence details remain collapsed by default.
Only selected profile values—not file bytes or résumé text—are cached in `sessionStorage`, keyed by
profile version, to protect edits across refresh; the entry is removed after activation.

## Transactions, failure, privacy, and rollback

Résumé validation, extraction, draft creation, suggestion persistence, and readiness persistence stay
inside the existing upload transaction. A failed replacement leaves the previous active/draft
profile unchanged. Activation resolves private taxonomy and writes profile/search state in the
existing transaction. Errors use the established product-safe profile envelope.

No original résumé bytes are retained after request processing, and no new third party, analytics,
LLM, or external résumé processor is introduced. Catalogue reads and custom values retain workspace
ownership. The migration is additive except for relaxing the market-location constraint; rollback is
a source rollback while retaining V26 data. Requiring nonblank locations again would need an explicit
forward migration after checking country-wide rows and is not an automatic rollback step.

## Redacted quality fixture draft

The automated `reviewed-fixture-v1` engineering set contains no personal contact data and spans:

| Profession | Expected explicit skills/tools | Expected roles | False-positive trap |
| --- | --- | --- | --- |
| AI/full-stack engineering | Python, Machine Learning, TypeScript, React | AI Engineer, Frontend Engineer | `medium.com` contact line and short `AI` skill fragment |
| Nursing | Nursing, Patient Care, Clinical Documentation | Registered Nurse | prose outside the explicit skill list |
| Enterprise sales | CRM, Account Management, Stakeholder Management | Account Executive | generic growth prose |
| Data analysis | SQL, Data Analysis, Power BI, Microsoft Excel | Data Analyst | alias normalization for PowerBI and Excel |

The executable threshold is at least 90% recall and 95% precision for explicit active-taxonomy
skills. This set is an engineering control derived from the selected requirements; product-owner
review of the fixture expectations is still required before it is labelled the final owner-reviewed
quality set. Parsing p95 through the supported file types and 5 MB boundary also remains release
evidence, not an unmeasured claim.

## Required verification

- extractor defect and cross-profession quality tests;
- React helper/render/accessibility-state tests;
- controller catalogue and safe validation tests;
- PostgreSQL Testcontainers for V1–V26, canonical/private sectors, country-wide persistence,
  profile-version compatibility, and ownership;
- provider and portal request tests for blank locations;
- clean Maven suite, Spotless, `git diff --check`, packaged-JAR inspection, and responsive browser
  checks for success, warning, error, replacement, keyboard, and reduced-motion states.
