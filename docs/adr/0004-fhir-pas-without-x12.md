# ADR 0004: Direct FHIR PAS, no X12 278

Status: accepted

## Context

HIPAA names X12 278 as the standard for prior auth transactions. The PAS IG describes an intermediary that translates FHIR to X12 and back. CMS announced enforcement discretion, so payers may offer the FHIR prior auth API from CMS-0057-F without X12 278.

## Decision

payer-api accepts PAS bundles at `Claim/$submit` and adjudicates them directly. No X12 translation.

## Why

- The demo is about the FHIR workflow (CRD → DTR → PAS). X12 translation adds a large mapping effort and no learning value here.
- It matches the direction the regulation allows.

## Trade-offs

- A real deployment that needs X12 would put a translator between clinic-api and payer-api. We note this in the docs and keep the PAS bundle conformant, so a translator could be added later.
