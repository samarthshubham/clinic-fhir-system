# ADR 0007: One coverage rules source for CRD, DTR and PAS

Status: accepted

## Context

Three places decide about coverage: CRD (is prior auth needed?), DTR (which form?) and PAS (approve, deny or pend?). If each one had its own logic, they could disagree. For example, CRD could say "no PA needed" while PAS denies the request.

## Decision

A single `coverage-rules.yaml`, loaded and validated by `CoverageRuleService`, maps a service code to:

- `paNeeded`
- `docNeeded`
- the questionnaire canonical
- `autoApproveCriteria`
- `emergencyExempt`

CRD, DTR and PAS all ask this service.

## Why

- Consistent answers across the three IGs, and one place to change a rule.
- Rules are reviewable data with table-driven tests, not scattered code.

## Trade-offs

- YAML is not a real rules engine; complex medical policy would outgrow it. A real payer would use a policy engine or CQL-based coverage rules.
- Rule changes need a restart. Fine for a demo.
