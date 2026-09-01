# JobLens ATS Readiness Rule Boundary

This document defines the public evidence and deterministic rules used by the M2.7 resume
machine-readability advisor. It is not a reproduction, certification, or prediction of any
proprietary applicant-tracking system.

## Public vendor guidance reviewed

- Greenhouse documents unsuccessful or partial parsing risks from image-only files, spaced letters,
  graphics, tables, headers, footers, text boxes, columns, unclear sections, and incomplete job
  titles. It also advises users to review parsed information because parsing can be wrong:
  [Unsuccessful resume parse](https://support.greenhouse.io/hc/en-us/articles/200989175-Unsuccessful-resume-parse)
  and
  [MyGreenhouse candidate FAQ](https://support.greenhouse.io/hc/en-us/articles/43418495049499-MyGreenhouse-FAQ-for-Candidates).
- Workable describes parsing candidate name, email/contact information, social links, and other
  profile fields from uploaded resumes:
  [Uploading candidate resumes](https://help.workable.com/hc/en-us/articles/115012661408-Uploading-candidate-resumes-CVs-Individual-and-bulk-options).
- SAP SuccessFactors describes resume parsing into discrete candidate-profile fields and identifies
  contact details, current title, work experience, and education as structured profile information.
  Supported formats and behavior vary by configured system:
  [Working with resume parsing](https://help.sap.com/docs/successfactors-recruiting/setting-up-and-maintaining-sap-successfactors-recruiting/working-with-resume-parsing)
  and
  [Candidate profile](https://help.sap.com/docs/successfactors-recruiting/setting-up-and-maintaining-sap-successfactors-recruiting/candidate-profile-in-sap-successfactors).

The common, defensible conclusion is narrow: parsers attempt to turn readable document content into
structured fields, formatting can interfere, and users must review the result. Vendors do not expose
one universal scoring algorithm.

## JobLens observable rules

Assessment version: `readability-v1`.

The score starts at 100 and applies deterministic deductions. It measures only the likelihood that
ordinary text extraction can locate common resume information.

| Finding | Observable rule | Deduction | Review required |
| --- | --- | ---: | --- |
| Contact details | Readable email address or phone number | 15 when absent | No |
| Standard sections | At least two of Summary, Experience, Skills, Education | 20 when limited | No |
| Job-title line | Plain-text title/organization style line | 10 when absent | No |
| Employment dates | Explicit year range such as `2022 - Present` | 10 when absent | No |
| Education | Standard Education/Qualifications heading | 10 when absent | No |
| Parsing quality | No repeated spaced-letter words or excessive replacement characters | 25 when poor | Yes |
| Length | At most 2,000 extracted words | 5 when exceeded | No |
| DOCX table | `<w:tbl>` markup | 5 | No |
| DOCX text box | text-box markup | 15 | Yes |
| DOCX header/footer | header or footer document part | 10 | Yes |
| DOCX drawing | drawing/anchored-object markup | 5 | No |
| Document uncertainty | At least two vacancy/interview signals and fewer than two resume headings | 30 | Yes |

Every finding has a stable code, category, severity, plain-language remediation, bounded evidence,
and deduction. `REVIEW_REQUIRED` means the readable draft is preserved and must be acknowledged
before activation; it does not mean the candidate or document failed an ATS.

## Hard failures

Upload fails only when the file is absent, larger than 5 MB, not a supported PDF/DOC/DOCX type,
cannot be parsed safely by the configured parser, or yields fewer than 80 readable characters.
Readable but unusual or suspicious content becomes a reviewable draft.

## Deliberate limitations

- Original resume bytes and extracted text are not retained. Only metadata, hashes, assessment
  measures, and bounded evidence are stored.
- PDF column, image, and visual-layout detection is not guessed from plain extracted text. DOCX risks
  are reported only when the corresponding package markup is measurable.
- The score does not measure writing quality, experience, seniority, employability, or job fit.
- Keyword alignment is excluded. It requires an explicit target role or job description and must be
  a separate future feature.
- JobLens does not automatically rewrite a resume and does not use an LLM for this assessment.
