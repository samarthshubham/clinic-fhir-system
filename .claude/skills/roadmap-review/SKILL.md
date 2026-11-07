---
name: roadmap-review
description: Check a clinic-fhir-system pull request against the payer / Da Vinci roadmap (its story's acceptance criteria, required tests and data, the ADRs, NFRs and the end-to-end goal) and write a scorecard. Use in the roadmap-review CI job, or locally with "/roadmap-review <pr-number>".
---

# Roadmap adherence review

The question this review answers is **"Does this PR deliver its roadmap story the way the roadmap says, and does it keep us on track for the end-to-end goal?"** It does not hunt for general bugs; `incremental-review` does that. Stay in your lane and don't repeat its findings.

The roadmap is `docs/roadmap/payer-davinci.md`. Read it first, every time, because it changes.

## Inputs

CI passes `pr=<n> repo=<owner/name> story=<E?-S?>`, where `story` comes from `.github/scripts/roadmap-guard.sh`.

Locally, given only a PR number, take the story from the `Roadmap story:` line of the PR body (step 1.3). In local runs, **print** the scorecard. Do not write files or post anything unless the user asks. Without a PR number (a local diff), write `local diff` in the header instead of `PR #<n>`.

**Payer paths**, the same as in `roadmap-guard.sh`: `payer-api/`, `care-platform/apps/payer-app*`, `fixtures/davinci/`, the `client` and `interop` packages of clinic-api, and `care-platform/apps/seed-demo-data/src/features/payer-*`.

## 1. Gather

1. Read `docs/roadmap/payer-davinci.md`: §3 (end-to-end goal), §4 (NFRs), §5 (test data), and the story's own section `#### <story> · …`.
2. Read the ADRs the story cites (`docs/adr/`) and `docs/16-payer-test-strategy.md` §1 for what each test level means.
3. Get the PR: `gh pr view <n> --json title,body,headRefOid,baseRefName,files`, then the whole diff with `git diff origin/<base>...HEAD`. Review the whole PR every time, not only new commits; the scorecard is about the PR as a whole.
4. Get the linked issue if there is one (`Closes #n`): `gh issue view <n> --json body`. If the issue's AC differ from the roadmap, the roadmap wins; list the difference under follow-ups.

If the PR has no story but touches payer paths, the verdict is **Off-roadmap**: write the scorecard with only the Linkage and Scope rows filled in.

## 2. Score

Give every item a status: **met**, **partial**, **missing** or **n/a**, with evidence. Evidence is a `file:line`, a test class and method, or a fixture path. "Looks fine" is not evidence. If you can't find evidence, the item is missing.

### a. Acceptance criteria (must)
Every AC checkbox of the story. Partial means some of it is there. Say which part is missing.

### b. Required tests (must)
For each level in the story's **Tests** line, find a test at that level that **asserts the behavior**, not just one that runs it. In particular:
- **authorization matrix:** new endpoints and operations have rows, including negative cases (wrong role, wrong scope, other member).
- **contract:** compares against a file in `fixtures/davinci/`.
- **interop server:** goes over HTTP against `/fhir` or `/cds-services`.
- **unit** tests for time-based logic use a fixed `Clock`.

### c. Required data (must)
Each item on the story's **Data** line exists: fixtures, seed features, `coverage-rules.yaml` rows. New FHIR resource types or new outcomes come with golden fixtures. The §5 outcome matrix must not regress: a seeded profile must keep its outcome.

### d. ADRs (must)
Check the ADRs the story cites, plus these always:
- **0002:** `org.hl7.fhir.*` only in `interop`, `cdshooks`, `mapper` and `service`. JSON endpoints return DTOs with `{status, message}` errors; interop endpoints return FHIR with `OperationOutcome`.
- **0001:** clinic-api never reads the payer HAPI directly, and payer-api never reads the provider HAPI. They talk only through each other's APIs.
- **0007:** coverage decisions come from `CoverageRuleService`, and its rules come from `coverage-rules.yaml`. Hard-coded codes anywhere, including inside `CoverageRuleService`, are a violation.
- **0003:** no home-made exporter, validator or notifier where HAPI already has one.

A deviation is allowed only when the PR description's "ADR deviations" section explains it. Then mark it **partial** and quote the reason.

### e. NFRs (should, must when the story cites them)
For each NFR the story cites, check that it is implemented and tested: deadline + `Clock`, denial reason, scopes, compartment, opt-out, consent, webhook secret, `AuditEvent`, `Provenance`, idempotency, fail-open, `$inquire` fallback, `meta.profile`.

Two checks apply to every PR, whether or not the story cites them, and count as **must**:
- **NFR-SEC-6:** no new `log.` call writes names, birth dates, member ids, phone numbers or emails.
- **§5 data rules:** fictional identities only, and no real credentials. Using CPT where HCPCS, LOINC or SNOMED would do is a *should*.

The §5 outcome-matrix regression check needs seeds and callers. Before those stories exist, judge it from the rules and code, and say in the evidence that it was reasoned, not run.

### f. End-to-end goal (must)
- Does the change break or bypass any step of the §3 journey? For example, an order that skips CRD, or a decision with no notification path.
- When the PR changes a journey step's behavior, is the `@journey @payer` scenario (once it exists) updated in the same PR?
- Do the IG names on the wire match the spec: hook ids, operation names (`$submit`, `$inquire`, `$questionnaire-package`, `$submit-attachment`, `$member-match`), the `coverage-information` extension and `Claim.use = preauthorization`? A wrong name means Inferno fails, so treat it as a must.

### g. Scope and docs (should)
- **Scope:** files outside the story's area. List them; they're fine if small and explained.
- **Docs:** CapabilityStatement, `openapi.yaml`, `docs/` and `CLAUDE.md` are updated when endpoints or setup change. When the PR changes scope, the roadmap file is updated in the same PR.

## 3. Verdict

- **Aligned:** no must item is missing or partial.
- **Gaps:** at least one must item is missing or partial.
- **Off-roadmap:** payer paths changed with no valid story.

The CI job fails on **Gaps** and **Off-roadmap**. Partial items explained by a documented ADR deviation don't count against Aligned.

## 4. Output

In CI, write `roadmap-review.md` in the repository root. The workflow posts it as a sticky comment, so do not add `<!-- … -->` markers. The **first line must be exactly** `Verdict: Aligned`, `Verdict: Gaps` or `Verdict: Off-roadmap`; the workflow reads it.

```markdown
Verdict: <Aligned | Gaps | Off-roadmap>

## Roadmap review: <story> · <story title>

PR #<n> · reviewed at `<head short sha>`

### Acceptance criteria
| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | <AC text, shortened> | met | `payer-api/src/.../ClaimSubmitProvider.java:48` |

### Required tests
| Level | Status | Evidence |
|---|---|---|
| unit | met | `AdjudicatorTest.deniesWhenConservativeTherapyUnderSixWeeks` |

### Required data
| Item | Status | Evidence |
|---|---|---|

### ADRs and NFRs
| Check | Status | Evidence / note |
|---|---|---|

### End-to-end goal
<One short paragraph: which journey steps this PR touches and whether they still hold. Name any wire-name mismatches.>

### Scope and docs
- <files outside the story, docs not updated, or "In scope; docs updated.">

### Must fix before merge
1. <missing or partial must item, with what to add and where>

(Write "Nothing; the story is delivered as planned." when there are none.)

### Follow-ups
- <should items, roadmap/issue differences, things for later stories>

### Not verified
- <tests not run here, behavior that needs the running stack, Inferno>
```

Keep it factual and short. Don't praise or restate the diff. Every row needs evidence or a reason.
