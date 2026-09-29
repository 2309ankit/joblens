# JobLens — hackathon submission draft

Requirement: HACKATHON-01. Prepare a truthful, reproducible submission for the Nebius x NVIDIA
Global AI Hackathon without expanding the product milestone. This is a draft, not a submitted entry.

## Submission fields

- Project: **JobLens** (V1)
- Tagline: Explainable job discovery and NVIDIA-powered fit scoring, from résumé to application.
- Track: **Best Apps and Agents**
- Demo: https://joblens-demo.onrender.com
- Repository: https://github.com/2309ankit/joblens
- Public YouTube video: **pending recording and upload**
- License: **Apache License 2.0**, selected by the owner; [license text](LICENSE) added locally.

### Inspiration

Finding a role involves more than collecting vacancies. Job seekers need to decide which results
match their experience, understand the evidence behind that match, and keep track of what happens
next. JobLens brings those steps into one workflow while making its scoring decisions inspectable.

### What it does

JobLens reads a résumé into a reviewable draft. The candidate confirms skills, target roles and
search markets before activating a profile. The application discovers jobs through supported public
APIs, preserves source responses, normalizes postings, extracts skills, and detects duplicate
listings. A deterministic ranking policy creates a bounded shortlist. When enabled and within
budget, an NVIDIA model served by Nebius Token Factory supplies the final fit score and explanation.
The dashboard identifies the score source and offers expandable explanations (UI change locally
verified; deploy before recording). Candidates can save jobs, track application stages and
manage follow-ups.

### How we built it

The React interface is served by a Java 21 / Spring Boot modular monolith on Render. PostgreSQL on
Neon stores profiles, job evidence, application state, and Spring Batch execution checkpoints.
Flyway owns schema changes. Spring Batch provides explicit ingestion and reprocessing workflows.

The runtime integration calls Nebius Token Factory with structured candidate and job facts using
an NVIDIA open-source model. The September 18 live acceptance used
`nvidia/NVIDIA-Nemotron-3-Nano-30B-A3B`; recheck the configured model before recording the video.
Token Factory provides hosted inference without operating a GPU server in the application stack.

Model responses must satisfy a bounded JSON schema. Timeouts, invalid responses and exhausted call
budgets preserve deterministic scoring. Successful scores are cached against profile version, job
content, model and prompt version so unchanged inputs do not require another provider call.

An independently controlled query-planning feature can refine an existing search profile's query
and page budget before discovery. On September 29, live run 50 completed with `LLM_NEBIUS` query
provenance and a rationale for Adzuna, and deterministic planning for Jooble. The model retained
the existing Adzuna query based on prior results; this proves integration, not a relevance gain.

### Challenges

An early fuzzy-comparison implementation held a transaction too long during global comparisons.
Indexed candidate selection and checkpointed short writes enabled deployed runs to complete without
the previously observed idle-in-transaction failure. Another integration issue was a reasoning
response consuming the output budget before emitting the expected scoring JSON; the request was
adjusted to disable thinking for this bounded scoring task.

Free-host cold starts and synchronous search requests remain demo limitations. A disconnected
request does not necessarily mean the durable job failed; inspect its recorded state before retrying.

### What we verified

The September 18 acceptance record demonstrates successful live NVIDIA scoring, cache reuse without
another recorded provider attempt, and completion of the deployed fuzzy step over 1,460 normalized
jobs. These prove integration behaviour, not ranking accuracy or production-scale performance.

### What is next

Evaluate ranking against reviewed examples and improve
asynchronous run feedback. Authentication, privacy workflows and production reliability remain
separate launch milestones. The hosted demo is for synthetic or redacted résumé data.

## Technology feedback draft — owner review before submission

Token Factory let this Java application call an NVIDIA model without maintaining GPU infrastructure.
During integration, we encountered a null scoring-content response when reasoning exhausted a small
output budget. A model-specific example documenting reasoning controls and expected structured
output would have shortened diagnosis. We would also value clear examples for bounded JSON
responses, token-budget tuning and catalogue/model lifecycle changes. The application-side cache
and provider-attempt ledger made repeated inference easier to inspect and control.

This feedback is grounded in repository incident records. Review it in your own voice before posting.

## Work during the submission period

The published period starts August 26, 2026. The first repository commit is August 28, 2026
(`d5c013c`). If earlier work existed outside Git, identify that baseline explicitly in Devpost.
Git records include these later improvements:

- September 14: guarded NVIDIA-on-Nebius scoring (`316a1e3`).
- September 15: model-output compatibility fix (`080946d`) and optional agentic query planning
  (`e51cab2`).
- September 16: indexed/checkpointed fuzzy deduplication (`69df31c`) and provider retry fixes.
- September 17: welcome guide (`7b65be8`).

Do not describe earlier work as created during the event without checking its dates.

## Submission gate

- [x] Owner selected Apache 2.0; canonical license added locally with README and Maven metadata.
- [ ] License changes published and detected on the public repository's default branch.
- [ ] Current full demo rehearsal passes; model, runtime flags and limits recorded.
- [x] Query planning live-verified in run 50; the model retained the original query.
- [x] React evidence display implemented and locally verified (HACKATHON-UI-01).
- [ ] Deploy and verify the evidence display on the public demo before recording.
- [ ] README and judge instructions available at the submitted repository revision.
- [ ] Public English YouTube video under three minutes, with spoken NVIDIA/Nebius explanation.
- [ ] Description, technology feedback and development-period statement reviewed.
- [ ] Devpost registration and draft fields completed; final submission confirmed.
- [ ] Demo remains accessible through the end of judging, December 15, 2026, Pacific time.

Deadline: October 30, 2026, 10:00 AM PDT / October 31, 2026, 1:00 AM Singapore time.
Source: https://nebiusglobalaihackathon.devpost.com/rules (checked September 29, 2026).
