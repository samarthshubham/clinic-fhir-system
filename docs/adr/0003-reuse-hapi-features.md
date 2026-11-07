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

- We depend on HAPI's behavior and version (7.4.0).
- Proxying bulk export means rewriting the status and file URLs so clients never reach the payer HAPI directly.
- Criteria-based rest-hook subscriptions are not the topic-based subscriptions some newer IG versions prefer. We document that gap.
