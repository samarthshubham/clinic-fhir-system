# ADR 0003: Reuse HAPI JPA features instead of building them

Status: accepted

## Context

Several requirements are generic FHIR server features: bulk data export (Provider Access, Payer-to-Payer), profile validation (PAS, CDex) and change notifications (PAS decisions).

## Decision

- Bulk export uses HAPI JPA `$export` on the payer HAPI. payer-api checks attribution, opt-out and scopes, then proxies the kick-off and status endpoints.
- Validation uses `$validate` on the payer HAPI, which has the IG packages loaded. In CI, the HL7 validator CLI checks the golden fixtures.
- Notifications use rest-hook `Subscription`s with a shared secret, the same pattern as the high BP alerts (`HighBpAlertService`, `AlertController`).

## Why

These features are mature, spec-conformant and already running in our stack. Writing our own exporter or validator would take weeks and be less correct.

## Trade-offs

- We depend on HAPI's behavior and version: 7.4.0 for the provider HAPI, 8.12.0 for the payer HAPI. 7.4.0's validator rejects valid PAS 2.2.1 bundles, so the payer HAPI moved to 8.12.0 with E4-S2.
- HAPI validates codes only against code systems it holds. CPT, HCPCS, ICD-10-CM and X12 are not in any IG package, so `terminology/codesystems/` seeds `fragment` CodeSystems with the demo codes, and required-bound CodeableConcepts use one code system.
- Proxying bulk export means rewriting the status and file URLs so clients never reach the payer HAPI directly.
- Criteria-based rest-hook subscriptions are not the topic-based subscriptions some newer IG versions prefer. We document that gap.
