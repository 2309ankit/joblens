# S0.4 Semantic Skill Extraction

Status: **IMPLEMENTED 2026-09-11; shipped disabled by default.** The full pipeline (DJL/ONNX Runtime
embedding model, `SemanticSkillMatcher`, `ProfileIntelligenceExtractor` second pass, migration `V29`,
threshold config, tests) is built, tested (155 Java tests, 0 failures/errors), and Spotless/diff-clean.
It is **not active in the shipped default** (`joblens.onboarding.semantic-matching.enabled=false`)
because building and running the actual Docker image surfaced a real incompatibility that no amount of
`mvn test` (which runs on macOS, not the Alpine deployment image) could have caught — see
[8](#8-implementation-evidence-2026-09-11) for the full account. Turning it on in production requires a
runtime base-image decision (§8) that was deliberately deferred rather than made unilaterally.

This checkpoint is bounded to `UX-ONBOARDING-05` (recall gap first observed 2026-09-11 on a
sales/account-management résumé; interim content patch was migration `V28`, see `SESSION_HANDOFF.md`).
S0.2 acceptance, S0.3 ranking correctness, authentication, résumé object storage, a corpus-wide
unknown-term feedback loop, and any LLM pre-pass are explicitly excluded — see
[5](#5-explicitly-out-of-scope).

## 1. Problem and current architecture

`ProfileIntelligenceExtractor` (`esco-deterministic-v4`,
`src/main/java/com/ankit/joblens/onboarding/ProfileIntelligenceExtractor.java`) matches résumé text
against the skill/role catalog using `PhraseAutomaton`
(`src/main/java/com/ankit/joblens/intelligence/PhraseAutomaton.java`), a generic Aho-Corasick trie that
does **exact, case-folded, token-boundary phrase matching** — no fuzziness, no synonyms beyond whatever
rows exist in `skill_alias`/`role_alias`. A phrase not literally catalogued scores zero recall no matter
how obviously synonymous it is (e.g. "Cold Outreach & Email Sequencing" vs. catalogued "Outbound
Prospecting").

The live local catalog, queried directly against the running Testcontainers-equivalent dev database
today, is **much larger than the "~100 rows" estimate recorded in `NEXT_MILESTONES.md`**:

| Table | JOBLENS-seeded | ESCO-imported | Total |
| --- | ---: | ---: | ---: |
| `skill` | 123 | 8,527 | 8,650 |
| `skill_alias` | — | — | 3,426 |
| `role_catalog` | 40 | 2,926 | 2,966 |

That correction matters for the design below: brute-force cosine similarity is still cheap at this
scale (~11,600 skill+alias terms × 384-dim float vectors ≈ 18 MB resident, and a few million
multiply-adds per résumé candidate phrase is sub-millisecond on any modern CPU), so a vector database
or approximate-nearest-neighbor index is still not warranted — but it must be verified against the real
row count, not the stale estimate, and the design must not assume the catalog stays small: ESCO imports
can grow it further.

`SkillExtractor` (same package, not the résumé-facing `ProfileIntelligenceExtractor`) already has the
one pattern worth reusing: it caches a built `PhraseAutomaton` behind a `volatile` field keyed by
`SkillCatalogService`'s catalog **signature**, rebuilding only when the active taxonomy version changes.
No comparable "load once, cache, invalidate on catalog change" mechanism exists yet for a bundled ML
model or precomputed embeddings — this checkpoint would be the first.

## 2. Requirement and actor

The actor is the same anonymous workspace user uploading a résumé during onboarding. Today, a résumé
phrase that is semantically a known catalog skill but not a literal/alias match is silently dropped —
neither detected nor surfaced as a suggestion, unless it happens to fall inside an explicit "Skills:"-
style list segment (in which case it becomes a `SUGGESTED` `TermSuggestion` with no catalog mapping at
all).

The selected checkpoint must, without changing today's exact-match behavior for terms that already
match:

1. catch résumé phrases that are semantically close to a catalog skill but share no literal wording
   with it, and either auto-accept them as that canonical skill (high similarity) or surface them as a
   reviewable, catalog-mapped suggestion (medium similarity) instead of an unmapped free-text guess;
2. do this with a **local** model only — no external embedding API, no network call, no per-request
   marginal cost (locked decision, see `NEXT_MILESTONES.md`);
3. keep extraction deterministic and inspectable: the same résumé text on the same catalog/model version
   must always produce the same matches, and every semantic match must expose its similarity score and
   matched canonical term the same way `TermHit`/`TermSuggestion` already do;
4. not regress `esco-deterministic-v4`'s existing exact-match precision, contact-noise rejection, or
   short-fragment safety filtering — those are proven behavior with passing regression tests.

## 3. Design

### 3.1 Augment, not replace

`PhraseAutomaton` exact/alias matching stays the **first pass**, unchanged, for both skills and roles.
It is zero-false-positive-risk for literal matches and existing tests depend on its exact behavior.
Semantic matching runs as a **second pass**, scoped only to:

- résumé candidate phrases that did **not** produce an exact/alias `TermHit` (the same population that
  today either gets silently dropped or becomes an unmapped `explicitCandidates` `SUGGESTED` term), against
- catalog terms that were **not** already exactly matched in this résumé (no need to re-score something
  already found).

This bounds the added compute to "unmatched résumé phrases × full catalog," not "full résumé × full
catalog," and means the change is additive to `ProfileIntelligenceExtractor` rather than a rewrite of
its proven matching core.

### 3.2 Model and runtime

Bundle `all-MiniLM-L6-v2` (or an equivalent ~90 MB sentence-transformer with a permissive license) as an
ONNX export, run via a JVM ONNX inference runtime. Candidates:

- **DJL (Deep Java Library) + its ONNX Runtime engine + HuggingFace tokenizers extension** — has
  first-class HuggingFace tokenizer support (needed for the model's WordPiece vocabulary) and is the
  more idiomatic Java integration.
- **Raw ONNX Runtime Java API** — smaller dependency footprint, but tokenization would need to be
  hand-rolled or a separate small tokenizer library added.

This choice is an open decision (§6.1), not made here.

### 3.3 Catalog embeddings: compute-once, cache, invalidate on version change

Mirror `SkillCatalogService`'s existing signature-based cache pattern rather than inventing a new one:

- On first use (or app startup), embed every active skill/alias/role canonical term once, batched,
  producing an in-memory `float[][]` keyed by term id.
- Cache this behind the same kind of catalog-version signature `SkillExtractor` already uses; invalidate
  and recompute only when the active taxonomy release changes (ESCO import), not on every request.
- Whether this cache is **recomputed in memory at every app startup** (~11,600 terms batched through the
  model — a fixed cold-start cost to measure, §3.5) or **persisted** (e.g. a `bytea`/`vector` column
  storing each term's embedding, refreshed only when the taxonomy release changes) is an open decision
  (§6.2). Persisting avoids repeating the encode cost on every deploy/restart but adds migration/storage
  surface for a value that's cheap to regenerate; recomputing at startup is simpler but adds to the
  already-measured 193-second D3 cold-start time (§3.5 must quantify how much).

### 3.4 Matching and disposition

For each unmatched résumé candidate phrase: embed it once, compute cosine similarity
`(A·B)/(‖A‖×‖B‖)` against every unmatched catalog term's cached vector, take the best match.

Reuse the existing `TermHit`/`TermSuggestion` disposition model rather than adding a third state:

- similarity ≥ high threshold → treated as a `TermHit` (same as an exact match today), with
  `canonical=false` and a new `matchKind=SEMANTIC` (or equivalent) so the UI/API can still distinguish
  "found this exact wording" from "found this by meaning" on inspection;
- similarity in the mid band → a `TermSuggestion` with `reviewState=SUGGESTED`, carrying the matched
  canonical term and its similarity score as evidence, instead of today's unmapped free-text guess;
  below the mid band → unchanged current behavior (unmapped candidate or dropped).

The 0.80 / 0.55 thresholds recorded in `NEXT_MILESTONES.md` are **provisional placeholders from the
owner discussion, not calibrated values** — they must be set from the fixture exercise in §4, the same
way S0.2's 90%/95% recall/precision targets were fixture-derived rather than guessed.

### 3.5 Docker image and Render free-tier budget

The current runtime image (`eclipse-temurin:21-jre-alpine` + one fat jar, no layering/slimming) has no
existing memory reservation for anything like this. Before implementation, measure and record:

- final image size delta from adding the model file + ONNX/DJL runtime + native libs (the model itself
  is ~90 MB; the runtime's native dependencies typically add tens of MB more — needs a real build to
  confirm, not an estimate);
- resident memory delta at idle and during a résumé upload, against the 512 MB Render free-tier
  container limit (D3 baseline: ~240 MB idle at 512 MB/0.1 CPU — see `SESSION_HANDOFF.md`);
- added cold-start time against the existing 193-second D3 baseline, and per-résumé inference latency
  (model load is one-time; per-phrase encode is per-request) against the existing parsing-p95 gate that
  S0.2 acceptance also still owes;
- CPU-only inference behavior under Render's 0.1 CPU allocation specifically, since that is the
  tightest resource in the current free-tier profile, tighter than memory.

If the measured footprint doesn't fit, the fallback is documented in §6.4 rather than silently dropped.

## 4. Fixture and threshold calibration plan

Do not ship placeholder thresholds. Reuse the `reviewed-fixture-v1` discipline S0.2 established
(redacted, no personal contact data, spanning multiple professions) and extend it with a
**synonym/paraphrase column** the exact-match fixture didn't need:

| Profession | Literal catalog term | Résumé paraphrase (no shared words) | Expected disposition |
| --- | --- | --- | --- |
| Enterprise sales | Outbound Prospecting | "Cold Outreach & Email Sequencing" | semantic accept or suggest |
| Enterprise sales | Zoho CRM | "managed pipeline in Zoho" | semantic accept |
| AI/full-stack | Machine Learning | "built predictive models" | mid-band suggest, not auto-accept (too generic to auto-accept safely) |
| Data analysis | Microsoft Excel | "spreadsheet-based reporting" | mid-band suggest |
| (trap) any | (any) | a near-miss phrase that should **not** match (tests false-accept risk) | below threshold, no match |

Sweep threshold values against this fixture set and pick the pair that clears the same class of bar S0.2
used (≥90% recall / ≥95% precision on the labeled set) before hard-coding 0.80/0.55. Explicitly test the
false-accept trap rows — semantic matching's failure mode is different from exact matching's (confident
wrong answers, not silent misses), so precision on ambiguous/generic terms (e.g. "Machine Learning"
matching too broadly) needs its own scrutiny, not just aggregate recall.

## 5. Explicitly out of scope

Per the owner's locked scope decision in `NEXT_MILESTONES.md`:

- a corpus-wide feedback loop aggregating unknown terms across workspaces to auto-flag candidate catalog
  entries (needs new schema and an admin review/promotion surface — future checkpoint);
- an LLM first-pass extraction step ahead of embedding normalization (a second external/paid dependency
  and a departure from the extractor's deterministic design);
- any external embedding API (explicitly rejected on cost/dependency grounds);
- changes to `PhraseAutomaton`'s exact-match behavior, S0.2 acceptance items, S0.3 ranking correctness,
  or any provider/scoring/identity/deployment surface.

## 6. Open decisions — resolved 2026-09-11

1. **ONNX runtime library — DECIDED: DJL + ONNX Runtime engine + HuggingFace tokenizers extension.**
   Accepts the larger transitive dependency set in exchange for not hand-rolling WordPiece tokenization
   for `all-MiniLM-L6-v2`.
2. **Catalog embedding cache — DECIDED: recompute in memory at every app startup**, not persisted to the
   database. No schema/migration surface, no staleness/invalidation story to get right; the added
   one-time batched-encode cost against the existing 193-second D3 cold start must be measured and
   recorded as implementation evidence (§7), and revisited if that measurement makes it unacceptable.
3. **Auto-accept threshold risk tolerance — DECIDED: allow auto-accept above a high similarity
   threshold**, matching the original ~0.80 sketch in `NEXT_MILESTONES.md`. A similarity-only match
   above that calibrated threshold is treated the same as an exact `TermHit` (silently confirmed skill).
   The exact threshold value is not fixed here — it must still come from the §4 fixture sweep, with
   particular scrutiny on the false-accept trap rows given this is a new failure mode (confident wrong
   matches) exact matching never had.
4. **Fallback if the 512 MB budget doesn't fit — DECIDED: measure first, prefer a smaller/quantized
   model over deferring the checkpoint.** Don't pre-commit to a specific fallback model before real
   numbers exist. If the initial `all-MiniLM-L6-v2` ONNX build doesn't fit the free-tier budget once
   measured (§3.5), the default next step is trying an int8-quantized or smaller sentence-transformer
   variant, not immediately parking the checkpoint for a paid tier — but the actual choice is made from
   the measured image-size/memory numbers, not decided speculatively now.
5. **Go-ahead to begin implementation**: given 2026-09-11; implementation complete, see §8.

## 7. Required verification

- Unit tests for the embedding-match layer in isolation (mock/fixed vectors, no real model load) proving
  threshold boundaries, tie-breaking, and the augment-not-replace scoping.
- Integration tests loading the real model against the fixture set in §4, asserting the calibrated
  recall/precision bar.
- Regression proof that every existing `ProfileIntelligenceExtractorTests`/`ProfileIntelligenceQualityTests`
  exact-match case is unchanged (same `TermHit`s, same `canonical` flags, same rejected/ambiguous
  results).
- Packaged-image size, idle/upload memory, cold-start time, and per-résumé inference latency measurements
  against the D3 Render free-tier baseline, recorded as evidence rather than estimated.
- Clean Maven suite, Spotless, `git diff --check`, as every other checkpoint in this repository requires.

## 8. Implementation evidence (2026-09-11)

### Code delivered

- `TextEmbeddingModel`/`OnnxTextEmbeddingModel` (`com.ankit.joblens.intelligence.embedding`): loads
  `Xenova/all-MiniLM-L6-v2`'s **quantized** ONNX export (23 MB, not the 90 MB fp32 build — the smaller
  model was available and used from the start, not needed as a later fallback) via DJL + ONNX Runtime +
  HuggingFace tokenizers, mean-pooled and L2-normalized (`TextEmbeddingTranslatorFactory` defaults).
  Model/tokenizer/config files are fetched by `download-maven-plugin` (checksum-pinned) into
  `target/classes/models/all-MiniLM-L6-v2/` during `generate-resources`, the same convention the
  frontend build already uses for its own generated assets — not committed to git as binaries.
- `SemanticSkillMatcher`/`EmbeddingSemanticSkillMatcher` (`com.ankit.joblens.onboarding`): cosine-
  similarity matching against the shared catalog, eagerly warmed at `@PostConstruct` (decision §6.2),
  with workspace-private skills embedded lazily on first use. `SemanticSkillMatcher.NOOP` preserves
  exact-match-only behavior for the extractor's no-arg constructor (tests, non-Spring callers).
- `ProfileIntelligenceExtractor` (`EXTRACTOR_VERSION` bumped `esco-deterministic-v4` →
  `esco-semantic-v5`): the exact-phrase `PhraseAutomaton` pass runs unchanged first; a second pass then
  runs only unmatched explicit-list candidate phrases against unmatched skill definitions. A match at or
  above `auto-accept-threshold` becomes a `DetectedSkill` with `matchType="SEMANTIC"`; a match at or
  above `suggest-threshold` becomes a `TermSuggestion` carrying the matched canonical skill name in a
  new `matchedCanonicalTerm` field (migration `V29`, threaded through
  `insert-term-suggestion.sql`/`find-profile-term-suggestions.sql`/`ProfileIntelligence.TermSuggestion`);
  below that, behavior is byte-for-byte identical to `esco-deterministic-v4`.
- Configurable via `joblens.onboarding.semantic-matching.{enabled,auto-accept-threshold,suggest-
  threshold,model-directory}`, each independently overridable by environment variable.
- **Scope note**: implemented for **skills only**, not roles — the originating defect
  (`UX-ONBOARDING-05`) was a skill-recall gap; role matching has different context/section heuristics
  and was left out rather than folded in speculatively.

### Test evidence

- `ProfileIntelligenceExtractorSemanticMatchingTests` (fake `SemanticSkillMatcher`, no real model):
  proves auto-accept/suggest/none threshold dispositions and that already-exact-matched skills are
  excluded from the semantic candidate pool passed to the matcher.
- `OnnxTextEmbeddingModelTests` (real bundled model, no Spring context): confirms 384-dim output,
  synonym pairs score higher than unrelated pairs, and gives **real calibration evidence**: "Zoho CRM"
  vs. "managed pipeline in Zoho" scores **~0.50 cosine similarity** — meaningfully related (well above
  an unrelated-pair baseline) but *below* the provisional 0.55 suggest-threshold sketched in
  `NEXT_MILESTONES.md`. That threshold was always a guess pending the full fixture sweep in §4, which
  remains outstanding; this is one real data point, not a completed calibration.
- Full suite: 155 Java tests, 0 failures/errors. Two real bugs were found and fixed only by actually
  running the code against the bundled model (not visible from code review): DJL's `OrtModel` requires
  the ONNX file be named exactly `model.onnx` (default lookup, no `optModelName` set) — fixed by
  renaming on extraction; the export requires a `token_type_ids` input DJL's translator omits by default
  — fixed with `.optArgument("includeTokenTypes", true)`. A third bug, a `commons-compress` version
  conflict (DJL pulls 1.27.1; `tika-parsers-standard-package` needs 1.28.0, and Maven's nearest-wins
  mediation silently picked the wrong one, breaking PDF upload parsing) was caught by the existing PDF
  fixture test and fixed by pinning `commons-compress:1.28.0` explicitly in `pom.xml`.
- Spotless and `git diff --check` clean throughout.

### Docker/deployment evidence — the reason this ships disabled

Building and running the **actual** deployment image (`eclipse-temurin:21-jre-alpine`, the same base
D3 uses) — something no unit or `@SpringBootTest` run on macOS could exercise — surfaced a real
blocker:

1. First boot: `UnsatisfiedLinkError: libonnxruntime.so: Error loading shared library libstdc++.so.6:
   No such file or directory`. ONNX Runtime's published native library is glibc-linked; Alpine uses
   musl and ships no `libstdc++` by default.
2. Added `apk add --no-cache gcompat libstdc++` (the standard documented workaround for running
   glibc-linked binaries on Alpine) and rebuilt. **Still failed**, with a different error:
   `Error relocating libonnxruntime.so: __sprintf_chk: symbol not found`. `__sprintf_chk` is a glibc
   `_FORTIFY_SOURCE`-hardened symbol; `gcompat` is a partial shim and does not implement it. This is a
   confirmed, deeper limitation, not a missing-package fix.
3. Owner decision: ship with `joblens.onboarding.semantic-matching.enabled=false` as the default (env
   override `JOBLENS_SEMANTIC_MATCHING_ENABLED`), rather than switch the runtime base image away from
   Alpine unilaterally. The `gcompat`/`libstdc++` Dockerfile addition was reverted since it doesn't fix
   the problem and isn't needed while the feature defaults off.
4. Verified clean with the disabled default: rebuilt the real Docker image (`docker compose build app`
   picked up the property change), started it (`docker compose up -d --force-recreate app`) — **no**
   `UnsatisfiedLinkError`, `Started JoblensApplication in 2.01 seconds`, `/actuator/health` returns
   `{"status":"UP"}`, idle memory `301.2 MiB` (`docker stats`, no artificial memory limit applied), no
   ONNX-related log lines at all (the bean is never constructed when disabled).
5. **Image size caveat, real and unresolved**: even disabled, the packaged jar/image is still bloated,
   because the toggle only skips *loading* the model at runtime — it does not exclude the DJL/ONNX
   dependencies from the packaged jar. Measured: fat jar grew from a D3-era baseline to **279,540,128
   bytes** (~280 MB), and the final Docker image from the D3 baseline **167,668,389 bytes** (~160 MB) to
   **341,930,845 bytes** (~326 MB) — almost entirely three files:
   `BOOT-INF/lib/onnxruntime-1.21.1.jar` (**136.9 MB** — the default Maven Central artifact bundles
   native libraries for every OS/arch, not just Linux x64), `BOOT-INF/lib/tokenizers-0.33.0.jar`
   (18.6 MB), and the bundled `model_quantized.onnx` (23 MB). No Maven Central artifact publishes a
   Linux-x64-only ONNX Runtime build, so this bloat cannot currently be trimmed by a dependency-
   coordinate change alone. 326 MB still fits the 512 MB Render free-tier container, but with
   meaningfully less headroom than D3's ~160 MB baseline had, and this was **not** part of the original
   "~90 MB model" budget sketch in §3.5 — it is new evidence for a future session's capacity check
   before enabling this in production, independent of the base-image decision.

### What flipping this on in production requires (not done here)

1. A base-image decision: switch the runtime image to a glibc-based JRE (e.g.
   `eclipse-temurin:21-jre-noble`) — the only confirmed-working fix — and re-measure image size/memory
   on that base (glibc userland adds its own overhead on top of the numbers above).
2. The full §4 fixture sweep (multiple professions, trap rows) to replace the single real data point in
   the test evidence above with a calibrated threshold pair, the same discipline S0.2 applied to its
   90%/95% recall/precision bar.
3. Re-verification of the image-size/memory numbers on the new base image against Render's 512 MB limit,
   this time under an actual memory cap (this session's measurement had none applied).
4. Explicit owner sign-off to flip `joblens.onboarding.semantic-matching.enabled` to `true` in the
   Render environment once 1–3 are done.
