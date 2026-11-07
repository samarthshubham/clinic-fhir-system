# ADR 0005: DTR prepopulation with FHIRPath first, CQL later

Status: accepted

## Context

DTR forms are prefilled from the chart. The IG expects CQL Libraries, and evaluating CQL needs a heavy engine (`cqf-fhir-cr`). SDC also allows FHIRPath `initialExpression` and `x-fhir-query` variables, which the HAPI FHIRPath engine we already ship can evaluate.

## Decision

The first version prefills with FHIRPath and `x-fhir-query`, evaluated in clinic-api against the provider HAPI. CQL evaluation is a stretch story (E3-S5) with a parity test against the FHIRPath answers.

## Why

- No new heavy dependency before the end-to-end flow works.
- FHIRPath covers the demo forms (conditions, meds, encounters, dates).

## Trade-offs

- Not full DTR conformance until E3-S5. The Inferno DTR results will show the CQL gap, and we list it.
- The Questionnaires carry both FHIRPath and CQL expressions until then.
