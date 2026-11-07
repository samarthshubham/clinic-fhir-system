# ADR 0002: FHIR at the payer boundary, DTOs for our own apps

Status: accepted

## Context

clinic-api has a hard rule: FHIR types never reach controllers or DTOs, and every endpoint returns plain JSON. The Da Vinci IGs, however, *are* FHIR APIs. PAS `$submit`, PDex, Plan-Net and Formulary must be callable by any FHIR client: Inferno, a member's app, another payer.

## Decision

- payer-api has an `interop` package that serves FHIR through a HAPI plain-server `RestfulServer` at `/fhir`, plus a `cdshooks` package for CRD. These packages may use `org.hl7.fhir.*` types and return FHIR, with `OperationOutcome` errors.
- payer-api's `controller` and `dto` packages, used by payer-app, follow the clinic-api rule: DTOs only, `{ "status", "message" }` errors.
- clinic-api keeps its rule. Its only FHIR-facing endpoint is a new `interop` package that receives CDex `Task`s.
- ArchUnit enforces all of this in both modules.

## Why

- The standard is the contract on the boundary. Hiding it behind DTOs would make the APIs non-conformant and untestable with Inferno.
- Our own UIs still get small, stable DTOs, so FHIR detail doesn't leak into Angular.

## Trade-offs

- Two error formats in one service. The package decides which one applies.
- `RestfulServer` alongside Spring MVC needs care with security filters and servlet mapping.
