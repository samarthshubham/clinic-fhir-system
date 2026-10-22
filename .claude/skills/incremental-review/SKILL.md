---
name: incremental-review
description: Review a clinic-fhir-system pull request, but only the commits not reviewed before, against this repo's rules; posts inline findings and writes a summary file. Use in the claude-review CI job, or locally with "/incremental-review <pr-number>".
---

# Incremental PR review

Review **only what is new** in a pull request. Code that an earlier run already reviewed is context, not review material, and its findings must not be posted again.

## Inputs

CI passes `pr=<n> mode=<full|incremental> base=<sha> head=<sha> commits="<sha> <sha> …"` (oldest first), computed by `.github/scripts/review-state.sh range`.

Locally, if only a PR number is given, compute them yourself:

```bash
REPO=samarthshubham/clinic-fhir-system PR_NUMBER=<n> HEAD_SHA=$(gh pr view <n> --json headRefOid -q .headRefOid) .github/scripts/review-state.sh range
```

In local runs, **print** the review in the conversation. Do not post comments or write the summary file unless the user asks.

## 1. Read only the range

- `mode=incremental`: run `git show --stat --patch <sha>` for each sha in `commits`. Merge commits are already excluded; they bring in `main`, which was reviewed in its own PRs.
- `mode=full`: run `git diff <base> <head>`. This is the first review, or the branch was force-pushed or rebased.

Open surrounding files with Read, Grep and Glob when you need context. Only lines **added or changed in the range** may receive findings.

## 2. Load what was already said

```bash
gh api --paginate repos/<repo>/pulls/<pr>/comments --jq '.[] | {path, line, body}'
gh api --paginate repos/<repo>/issues/<pr>/comments --jq '.[] | select(.body | contains("<!-- claude-review summary -->")) | .body'
```

- **Do not re-raise** a finding that already exists on the same file and logic, even if its line number moved or the wording would differ.
- If a commit in the range fixes an earlier finding, list it under **Resolved since last review** in the summary.

## 3. What to look for

Report only findings you are confident are real: a concrete input or state that produces a wrong result, a crash, a security hole, or a broken project rule. No style nits; Spotless and Prettier enforce formatting.

Project rules, from `CLAUDE.md`:

**Backend (`clinic-api`)**
- FHIR types (`org.hl7.fhir.*`) never appear in `controller` or `dto` packages.
- Request bodies use `@Valid`. Errors use the `{ "status", "message" }` shape (`ErrorResponse`), never FHIR `OperationOutcome`.
- Spring Boot never queries PostgreSQL directly; all data goes through HAPI via the shared `IGenericClient`.
- **Every new or changed endpoint** has `@PreAuthorize("hasAuthority('<permission>')")` with the right permission from `RolePermissions`, and a matching row in `AuthorizationMatrixTest`. Public endpoints must be listed explicitly in `SecurityConfig`.
- No secrets, tokens or real credentials committed. Demo credentials must not reach production config or production bundles.
- Constructor injection only. Behaviour changes come with tests (JaCoCo floor: 90% lines).

**Frontend (`care-platform`)**
- Async work is RxJS: no `.subscribe()` + `signal.set()` pairs for HTTP; use `toSignal`, `switchMap` and the other operators. `takeUntilDestroyed()` for any unavoidable subscribe.
- Component SCSS is layout only. Colours come from `var(--px-*)` or `var(--mat-sys-*)`, never raw hex. No reimplemented Material components, no `::ng-deep` for colours.
- Route guards and hidden buttons are UX only; access control must also be enforced by the API.
- Specs updated for changed behaviour (Vitest, 90% line floor).

**Everywhere:** correctness, null handling, error handling that does not swallow failures, injection, and auth bypasses.

## 4. Be explicit about depth

A short review of a large range is only useful if it says what it skipped. While reviewing, keep track of:
- files you **read closely**
- files you only **skimmed** or **skipped**, and why (generated, lockfiles, Postman JSON, very large diffs)
- what you **could not verify** (tests not run, behaviour that needs a running stack)

For ranges over ~1,500 changed lines, put security-sensitive and logic-heavy files first: auth, controllers, services, mappers, interceptors and guards. Then tests, then docs.

## 5. Post inline findings (CI only, optional)

For findings tied to one line, also call `mcp__github_inline_comment__create_inline_comment` on that line. Start with the same severity tag and keep it to two to four sentences. The summary is the main output; inline comments only add to it.

## 6. Write the summary (CI only)

Write `review-summary.md` in the repository root. The workflow posts it as the single sticky PR comment and adds the "last reviewed" marker itself, so **do not** add any `<!-- … -->` markers.

```markdown
## Claude review: <incremental (N new commits) | full (N commits)>

Reviewed `<base short>..<head short>`.

**Scope.** Read closely: <files or areas>. Skimmed: <files>, because <reason>. Not reviewed: <files>, because <reason>.

### Findings
1. **<Bug | Security | Rule>: <one-line claim>.** `path/File.java:42`
   <Concrete scenario: the input or state, then what goes wrong.> <The fix.>
2. …

(If there are none, write: "No new findings in the reviewed range.")

### Security & patient data
<Only when auth, tokens, permissions, PHI, logging or external calls changed. Say what you checked and the result, including what is fine.>

### CLAUDE.md rules
- FHIR types out of controllers/DTOs: <ok | violation at …>
- `@Valid` and `{status, message}` errors: <ok | …>
- `@PreAuthorize` + `AuthorizationMatrixTest` row for new endpoints: <ok | n/a | …>
- Frontend RxJS-first and `--px-*` tokens: <ok | n/a | …>
- Tests updated with behaviour: <ok | …>

### Resolved since last review
- <earlier finding>: fixed in `<sha>`. (Omit the section on a first review.)

### Not verified
- <e.g. tests not run here; behaviour that needs the running stack>
```

Number the findings and order them by severity. Each one names a file and line, a concrete failure and a fix. If the range has nothing to report, still write the file with the Scope, Rules and Not verified sections. The job fails without it.
