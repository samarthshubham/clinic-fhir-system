# ADR 0006: Payer authentication and scopes

Status: accepted

## Context

payer-api has three kinds of callers: payer staff (reviewers) using payer-app, systems (clinic-api, another payer) and members (through patient-app). The Da Vinci IGs expect SMART on FHIR scopes; HRex recommends UDAP for B2B trust.

## Decision

- **Staff:** username and password login with JWTs and role → permission RBAC, the same pattern as clinic-api (`UM_REVIEWER`, `PAYER_ADMIN`).
- **Systems:** OAuth2 `client_credentials` at `POST /oauth/token` for registered clients, issuing SMART v2 system scopes (`system/Claim.c`, `system/Questionnaire.r`, …). A UDAP-style signed JWT client assertion is a stretch goal.
- **Members:** tokens with `patient/*.r` scopes and a `patient` claim. clinic-api gets them on the patient's behalf.
- A `RestfulServer` interceptor checks the scope of every interop operation and keeps members inside their compartment.

## Why

- It matches the scope model the IGs and Inferno test against, without running a separate identity provider.
- It reuses the JWT code and patterns we already have and test.

## Trade-offs

- payer-api is its own authorization server, which a real payer would not be. Swapping in a real IdP later means changing token issuance only, not the scope checks.
- No dynamic client registration.
