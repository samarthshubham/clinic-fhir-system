# ADR 0001: A separate payer service and FHIR store

Status: accepted

## Context

The payer needs members, coverage, claims, prior auths and a provider directory. We could add a `payer` package to clinic-api and store everything in the existing HAPI server, or run the payer as its own service with its own FHIR store.

## Decision

The payer is a separate Spring Boot service, `payer-api` (port 9095), with its own HAPI JPA server (port 8081, database `hapi_payer`) in the existing Postgres container.

## Why

- Real payers and providers never share a database. Every Da Vinci exchange crosses an organization boundary, and the demo should cross one too.
- With separate stores, identity matching becomes a real problem (patient vs member, `$member-match`) instead of something we fake.
- The payer HAPI can load IG packages and run validation without affecting the clinic's server.
- Each side can be tested, scaled and failed independently. A down payer must not break ordering (see NFR-REL-2).

## Trade-offs

- More moving parts: a second Spring service, a second HAPI, more CI time.
- Some auth and plumbing code is duplicated between the two services until a shared module is worth extracting.
- The seed data has to create matching records on both sides.
