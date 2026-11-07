# Roadmap: payer side and Da Vinci IGs (CMS-0057-F)

This is the source of truth for the payer work. The `roadmap-review` check (`.claude/skills/roadmap-review/SKILL.md`) judges every payer PR against this file. When scope changes, update this file in the same PR.

Related: [architecture](../11-payer-architecture.md) · [test strategy](../16-payer-test-strategy.md) · [ADRs](../adr/)

## 1. Why

The CMS Interoperability and Prior Authorization Final Rule (CMS-0057-F) asks impacted payers (Medicare Advantage, Medicaid, CHIP and QHPs on the federal exchange) for the following:

| Requirement | From |
|---|---|
| Prior auth decisions within **72 hours (expedited)** and **7 calendar days (standard)** | Jan 2026 |
| A **specific reason** for every denied prior auth | Jan 2026 |
| Public prior auth **metrics** | Mar 2026 |
| **Patient Access**, **Provider Access**, **Payer-to-Payer** and **Prior Authorization** FHIR APIs | Jan 2027 |

The HL7 Da Vinci Implementation Guides say how each API works in FHIR. This roadmap adds a payer to the project and connects it to the existing clinic, so the full prior auth and data exchange flows run end to end.

## 2. The IGs in plain terms

| IG | What problem it solves | Who calls whom | Key FHIR pieces |
|---|---|---|---|
| **HRex** (Health Record Exchange) | Shared building blocks for the other IGs | n/a | `$member-match`, common profiles |
| **CRD** (Coverage Requirements Discovery) | "Does this order need prior auth or paperwork?", answered while the clinician is ordering | EHR → payer via **CDS Hooks** | cards, `coverage-information` extension |
| **DTR** (Documentation Templates and Rules) | The payer's form, auto-filled from the chart | EHR fetches the form, fills it locally | `Questionnaire`, `$questionnaire-package`, `QuestionnaireResponse` |
| **PAS** (Prior Authorization Support) | Submit the prior auth and get a decision as FHIR, instead of fax, portal or X12 | EHR → payer | `Claim` (use = preauthorization), `$submit`, `$inquire`, `ClaimResponse` |
| **CDex** (Clinical Data Exchange) | Payer asks for more documents, provider sends attachments | Payer → EHR `Task`; EHR → payer `$submit-attachment` | `Task`, `DocumentReference` |
| **PDex** (Payer Data Exchange) | Share the data a payer holds | see below | US Core, `ExplanationOfBenefit` (with **CARIN Blue Button**), Bulk `$export`, `$member-match` |
| **ATR** (Member Attribution) | Which members belong to which provider | Payer maintains it | `Group` |
| **Plan-Net** | Public directory of in-network doctors and facilities | Anyone, no auth | `Practitioner(Role)`, `Organization`, `Location`, `InsurancePlan`, `Endpoint` |
| **US Drug Formulary** | Public list of covered drugs with tier and PA/step-therapy flags | Anyone, no auth | `InsurancePlan`, `Basic` (FormularyItem), `MedicationKnowledge` |
| **SMART App Launch / UDAP** | How apps and systems get tokens and scopes | n/a | `patient/*.r`, `system/*.c` |

The three PDex APIs differ in who asks:

- **Patient Access:** a member's app reads that member's own data.
- **Provider Access:** an in-network provider bulk-exports data for its attributed patients.
- **Payer-to-Payer:** a member's new payer pulls their history from the old payer, with consent.

PDex, Plan-Net and the other payer-facing IGs **are the API contract**. A member's phone app, the Inferno test kits or another payer must be able to call the payer with plain FHIR, without knowing our DTOs. That is why `payer-api` exposes real FHIR endpoints (ADR 0002), while `clinic-api` keeps its "JSON DTOs only" rule toward its own apps.

## 3. End-to-end goal

The roadmap is done when this journey runs as one `@journey @payer` Cucumber scenario, and the capability goals below each have passing tests.

**Prior auth journey (MRI lumbar spine):**

1. A clinician signs an MRI order. clinic-api calls the payer's CRD `order-sign` hook, and the order shows a **"Prior auth + documentation needed"** card.
2. The clinician opens the DTR form. clinic-api fetches `$questionnaire-package` and prefills answers from the chart; the clinician completes the rest.
3. The clinician submits the prior auth. clinic-api sends a PAS bundle to `Claim/$submit`, and the payer answers **pended**.
4. In payer-app, a reviewer sees the request in the queue with an **SLA countdown** and requests more documents. The payer sends a CDex `Task` to clinic-api.
5. The clinician attaches a note. clinic-api sends it with `Claim/$submit-attachment`, and the request goes back to the queue.
6. The reviewer **approves**. The decision is recorded with `Provenance`.
7. The payer's rest-hook notifies clinic-api, which calls `Claim/$inquire`. The clinician Inbox shows **"Approved, auth #…"**.
8. The patient opens patient-app → **My insurance** and sees the approved prior auth through the Patient Access API.

**Capability goals:**

- **Provider Access:** clinic-api bulk-exports attributed members from the payer and imports them with `Provenance`. Opted-out members are excluded.
- **Payer-to-Payer:** `$member-match` with consent returns the member, followed by a member-scoped export. Without consent the request is refused.
- **Plan-Net:** the clinician finds an in-network provider for a referral.
- **Formulary:** the medication dialog shows tier, PA flag and quantity limit for the patient's plan.
- **Conformance:** the Inferno Da Vinci test kits pass for CRD, DTR, PAS, PDex, Plan-Net and Formulary, or the remaining gaps are listed in `docs/16-payer-test-strategy.md`.

## 4. Non-functional requirements

Stories cite these by ID.

| ID | Requirement |
|---|---|
| **NFR-REG-1** | Every prior auth has a decision deadline: 72 h expedited, 7 days standard. It is computed with an injected `Clock`, and payer-app flags breaches. |
| **NFR-REG-2** | A denial cannot be saved without a reason code and reason text. |
| **NFR-REG-3** | Prior auth metrics: % approved, denied and pended, plus median decision time, per period. |
| **NFR-SEC-1** | Every interop call checks SMART scopes. A wrong or missing scope returns 403 as an `OperationOutcome`. |
| **NFR-SEC-2** | Patient Access only returns the member's own compartment. |
| **NFR-SEC-3** | Provider Access only returns attributed members and honors opt-out. |
| **NFR-SEC-4** | Payer-to-Payer requires the member's consent. |
| **NFR-SEC-5** | Webhooks between the services use a shared secret header. |
| **NFR-SEC-6** | No PHI in logs: log ids, not names, birth dates or member ids. |
| **NFR-AUD-1** | Every interop read or write writes a FHIR `AuditEvent`. |
| **NFR-AUD-2** | Every prior auth decision writes `Provenance`: who, when, why. |
| **NFR-REL-1** | `Claim/$submit` is idempotent on `Claim.identifier`. A repeated submit returns the first `ClaimResponse`. |
| **NFR-REL-2** | CRD calls time out after 2 s and **fail open**: the order is saved with a "coverage unknown" card. |
| **NFR-REL-3** | If a decision notification is lost, the client falls back to `$inquire` polling. |
| **NFR-CONF-1** | Every resource we build sets `meta.profile`. Golden fixtures pass the HL7 validator in CI. |
| **NFR-CONF-2** | Each IG epic ends with its Inferno test kit run. |
| **NFR-OBS-1** | Metrics for hook latency, decisions by outcome and decision time. Grafana gets a payer row and an SLA-breach alert. |

On the payer interop surface, error responses are FHIR `OperationOutcome`, as the IGs require. JSON endpoints in both APIs keep `{ "status", "message" }`.

## 5. Test data: one prior auth scenario per demo patient

Each of the 8 seed profiles owns one outcome, so every branch can be seeded and demoed. The journey e2e creates its own patient and MRI order and does not use these.

| Profile | Order (existing or new) | Coverage | Expected outcome |
|---|---|---|---|
| Priya Nair | CBC, Thyroid Panel (existing) | active | **No PA needed** (covered card) |
| Ramon Castillo | Continuous glucose monitor (new) | active | **Auto-approved**: DTR answers meet the criteria |
| Gerald Horton | Polysomnography (existing) | active | **Pended → docs requested (CDex) → approved** |
| Marcus Webb | Echocardiogram, urgent (existing) | active | **Expedited** (72 h clock) → approved |
| Sandra Okafor | MRI lumbar spine (new) | active | **Denied with reason**: under 6 weeks of conservative therapy |
| Tony Stark | Cardiac stress test (existing) | active | **Pended, SLA breached** (submission backdated) |
| Robert Osei | Emergency cardiac workup, stat (existing) | active | **No PA**: emergency services exempt |
| Aisha Chen | none | **inactive / not found** | CRD card "Coverage could not be verified" |

Data rules:

- All people and organizations are fictional. The payer is **Praxis Health Plan**.
- Member ids follow `PHP-#####`, and NPIs are fictional numbers that pass the NPI Luhn check.
- CPT is AMA-licensed: use only a handful of CPT codes and prefer HCPCS, LOINC and SNOMED.
- Dates are relative (`date-helpers.ts`).
- Seeds use identifier-based conditional creates so a rerun changes nothing.

## 6. Delivery plan

| Milestone | Epic | Outcome |
|---|---|---|
| M0 | E0 Foundations | infra, payer-api and payer-app skeletons, auth, provider prerequisites, test infra, this review pipeline |
| M1 | E1 Core data | members, coverage on both sides, coverage rules |
| M2 | E2 CRD | coverage cards on orders and appointments |
| M3 | E3 DTR | prefilled payer forms |
| M4 | E4 PAS + UM | submit, decide, notify: **the core demo** |
| M5 | E5 CDex | solicited and unsolicited attachments |
| M6 | E6 PDex | Patient Access, Provider Access, Payer-to-Payer |
| M7 | E7 Plan-Net + Formulary | public directory and drug lists |
| M8 | E8 Conformance + docs | Inferno, the journey e2e, Postman, docs, dashboards |

The critical path is M0 → M4. M5–M7 can then ship in any order. E8 stories run alongside the other epics, with S1 and S2 closing last.

One story = one PR. A PR names its story in the `Roadmap story:` line of the PR template and closes the story's issue.

## 7. Backlog

Every story lists **AC** (acceptance criteria), **Tests** (required test levels from the test strategy), **Data** (fixtures and seed changes), **Refs** (NFRs and ADRs) and **Tasks**. The `roadmap-review` check treats AC, Tests and Data as *must* items.

Size: **S** ≤ 1 day, **M** 2–3 days, **L** 4–5 days.

### E0 · Foundations

#### E0-S1 · Payer architecture docs, ADRs and diagrams
Labels: `payer` `davinci` `documentation` · Size: S
- **AC**
  - [ ] `docs/11-payer-architecture.md`, `docs/16-payer-test-strategy.md` and ADRs 0001–0007 exist
  - [ ] PNG diagrams for the architecture, the prior auth sequence and the PDex flows, each rendered from a committed `.mmd` source
  - [ ] This roadmap exists, and `CLAUDE.md` points to it
- **Tests:** n/a (docs)
- **Data:** n/a
- **Refs:** all ADRs
- **Tasks**
  - [ ] Write the docs and ADRs
  - [ ] Render the diagrams

#### E0-S2 · Payer HAPI server in infra
Labels: `payer` `davinci` `infra` · Size: S
- **AC**
  - [ ] `docker compose -f infra/docker-compose.yml up -d` starts `hapi-fhir-payer` on 8081, backed by database `hapi_payer` in the existing Postgres
  - [ ] US Core plus the HRex, CRD, DTR, PAS, CDex, PDex, CARIN BB, Plan-Net and Formulary packages are loaded, each version pinned after checking packages.fhir.org
  - [ ] Bulk export, rest-hook subscriptions and request validation are enabled
  - [ ] The provider HAPI is unchanged
- **Tests:** compose healthcheck; a smoke script `GET :8081/fhir/StructureDefinition?url=<PAS Claim profile>` returns 1
- **Data:** Postgres init script for the second database
- **Refs:** ADR 0001, ADR 0003
- **Tasks**
  - [ ] Init script
  - [ ] Compose service + healthcheck
  - [ ] IG env vars
  - [ ] Document the pinned IG versions in `docs/11-payer-architecture.md`

#### E0-S3 · payer-api skeleton
Labels: `payer` `davinci` `backend` · Size: M
- **AC**
  - [ ] `payer-api/` is a Spring Boot 3.5 / Java 25 Maven project on port 9095, with the same `config/ security/ controller/ service/ mapper/ dto/ exception/` layout as clinic-api plus an `interop/` package
  - [ ] A HAPI plain-server `RestfulServer` is mounted at `/fhir`, and `GET /fhir/metadata` returns a CapabilityStatement listing the supported IGs
  - [ ] ArchUnit: `org.hl7.fhir.*` types are only allowed in `interop`, `mapper` and `service`; the `controller` and `dto` packages stay FHIR-free
  - [ ] Spotless, JaCoCo (90% line gate), actuator + Prometheus endpoint, Husky hook
  - [ ] `ci.yml` and `changed-areas.sh` build and test payer-api when it changes; Prometheus scrapes it
- **Tests:** unit, ArchUnit, interop server (`@SpringBootTest` + HAPI client against `/fhir/metadata`)
- **Data:** n/a
- **Refs:** ADR 0002, NFR-CONF-1
- **Tasks**
  - [ ] Maven project and `FhirConfig` pointing at 8081
  - [ ] `RestfulServer` registration
  - [ ] `ArchitectureTest`
  - [ ] CI wiring
  - [ ] Prometheus scrape job

#### E0-S4 · payer-api authentication and scopes
Labels: `payer` `davinci` `security` · Size: L
- **AC**
  - [ ] Staff login for `UM_REVIEWER` and `PAYER_ADMIN`, following clinic-api's JWT + `RolePermissions` pattern
  - [ ] `POST /oauth/token` with `client_credentials` for registered clients (`clinic-api` first) issues tokens with SMART v2 system scopes such as `system/Claim.c` and `system/Questionnaire.r`
  - [ ] Member tokens carry `patient/*.r` scopes and a `patient` claim
  - [ ] A `RestfulServer` interceptor enforces scopes on every operation (NFR-SEC-1) and keeps members inside their compartment (NFR-SEC-2)
  - [ ] `AuthorizationMatrixTest` covers every JSON endpoint and every interop operation
- **Tests:** unit, web slice, authorization matrix, interop server (a wrong scope gets 403; member A reading member B gets 403)
- **Data:** client registration and demo staff accounts in a dev-only config, never in production config
- **Refs:** ADR 0006, NFR-SEC-1, NFR-SEC-2
- **Tasks**
  - [ ] Token service and `/oauth/token`
  - [ ] Scope interceptor
  - [ ] Staff login
  - [ ] `docs/15-payer-auth.md`

#### E0-S5 · Provider data prerequisites in clinic-api
Labels: `payer` `davinci` `backend` · Size: L
- **AC**
  - [ ] `Coverage` and payer `Organization` endpoints (`POST/GET /api/coverages`, `?patientId=`) with `coverage:read` and `coverage:write` permissions, `@PreAuthorize` and matrix rows
  - [ ] `ServiceRequestRequest` takes an optional coding (`codeSystem` + `code`) next to the text, and `ServiceRequestMapper` writes `ServiceRequest.code.coding[0]`
  - [ ] Patients get an MRN identifier. Practitioner NPI is still optional at creation, but the PA submit (E4-S1) rejects orders without it.
  - [ ] Mappers set the US Core `meta.profile` on Patient, Practitioner, Encounter, Condition, ServiceRequest and Coverage
  - [ ] `/snapshot` includes the active coverage
  - [ ] Docs updated: `docs/02-api-design.md`, `docs/03-fhir-mapping.md`, `openapi.yaml`, `CLAUDE.md`
- **Tests:** unit (mappers), web slice, authorization matrix, golden-file output validated against US Core (`ig-validate`)
- **Data:** seed steps in `apps/seed-demo-data` for coverage; golden fixtures `fixtures/davinci/provider/*.json`
- **Refs:** ADR 0002, NFR-CONF-1
- **Tasks**
  - [ ] Coverage resource end to end
  - [ ] ServiceRequest coding
  - [ ] Profiles in mappers
  - [ ] Snapshot field

#### E0-S6 · Insurance in clinician-app
Labels: `payer` `davinci` `frontend` · Size: M
- **AC**
  - [ ] The patient dashboard shows a coverage card: payer, plan, member id, status
  - [ ] A dialog adds or edits coverage with reactive-form validation on blur
  - [ ] The empty state for patients without coverage
- **Tests:** frontend unit (Vitest, 90% lines), UI e2e scenario "add coverage"
- **Data:** uses the E0-S5 seed
- **Refs:** design system rules in `CLAUDE.md`
- **Tasks**
  - [ ] Coverage card
  - [ ] Coverage dialog
  - [ ] Service and specs

#### E0-S7 · payer-app skeleton
Labels: `payer` `davinci` `frontend` · Size: M
- **AC**
  - [ ] Nx app `apps/payer-app` (density −1, sidenav: Queue, Members, Directory, Metrics) with a dev proxy to 9095
  - [ ] Login through `provideAuth({...})` from `libs/auth`
  - [ ] `apps/payer-app-e2e` with Cypress + Cucumber, reusing `libs/e2e-support`
  - [ ] CI lints, tests and builds payer-app when it changes
- **Tests:** frontend unit, UI e2e "reviewer logs in and sees an empty queue"
- **Data:** demo reviewer account (dev-only)
- **Refs:** ADR 0006
- **Tasks**
  - [ ] Generate the apps
  - [ ] Shell and routes
  - [ ] Auth wiring
  - [ ] CI

#### E0-S8 · Payer test infrastructure
Labels: `payer` `davinci` `testing` · Size: M
- **AC**
  - [ ] `fixtures/davinci/{provider,crd,dtr,pas,cdex,pdex,plan-net,formulary}/` exist and are added as test resources in both Maven modules
  - [ ] A CI job `ig-validate` runs the HL7 `validator_cli.jar` (pinned, cached) with the pinned IG packages over the golden fixtures and over `target/fhir-out/**` written by mapper tests. Any error fails the job.
  - [ ] `e2e.yml` also starts the payer HAPI and payer-api and runs `payer-app-e2e`
  - [ ] `libs/e2e-support` cleanup tasks cover the payer HAPI
- **Tests:** the `ig-validate` job itself; a deliberately broken fixture makes it fail (checked once, not committed)
- **Data:** the first golden fixtures from the IG examples, adapted to the demo identities
- **Refs:** NFR-CONF-1, test strategy §3
- **Tasks**
  - [ ] Fixtures layout
  - [ ] Validator job
  - [ ] e2e workflow
  - [ ] Cleanup tasks

#### E0-S9 · Roadmap adherence review pipeline
Labels: `payer` `davinci` `ci` · Size: M
- **AC**
  - [ ] The `.claude/skills/roadmap-review/SKILL.md` skill scores a PR against its story's AC, Tests and Data, plus the ADRs, NFRs and the end-to-end goal
  - [ ] `.github/workflows/roadmap-review.yml` runs on payer paths, posts one sticky comment and fails on missing *must* items
  - [ ] `.github/scripts/roadmap-guard.sh` fails fast when a payer PR has no story or changes payer-api main code without tests
  - [ ] PR template with a `Roadmap story:` line
- **Tests:** the guard is checked locally with `DIFF_RANGE`; the skill is run locally against a sample diff
- **Data:** n/a
- **Refs:** this file
- **Tasks**
  - [ ] Skill
  - [ ] Workflow
  - [ ] Guard
  - [ ] Marker argument in `review-state.sh`
  - [ ] PR template

### E1 · Payer core data and member identity

#### E1-S1 · Payer seed data
Labels: `payer` `davinci` `testing` · Size: M
- **AC**
  - [ ] `payer-plans.feature` seeds Praxis Health Plan and two `InsurancePlan`s
  - [ ] `payer-members.feature` seeds a member `Patient` + `Coverage` in the payer HAPI and a matching `Coverage` in the provider HAPI for the 8 profiles. Aisha Chen gets inactive coverage (§5).
  - [ ] `world.ts` and `api-client.ts` support a second base URL and a token for payer-api
  - [ ] Running `npx nx run seed-demo-data:seed` twice changes nothing
- **Tests:** seed-demo-data unit tests (coverage gate), a seed rerun idempotency check
- **Data:** this story is the data
- **Refs:** §5 data rules
- **Tasks**
  - [ ] Features
  - [ ] Step definitions
  - [ ] api-client changes

#### E1-S2 · Coverage rules
Labels: `payer` `davinci` `backend` · Size: M
- **AC**
  - [ ] `payer-api/src/main/resources/coverage-rules.yaml` maps codes to `{paNeeded, docNeeded, questionnaire, autoApproveCriteria, emergencyExempt}` for the services in §5 plus MRI lumbar spine
  - [ ] `CoverageRuleService` loads and validates the file at startup; an invalid file fails startup with a clear message
  - [ ] Lookups by code system + code; an unknown code means "no PA needed"
- **Tests:** unit (table-driven, one row per §5 scenario)
- **Data:** `coverage-rules.yaml`
- **Refs:** ADR 0007
- **Tasks**
  - [ ] Rule model
  - [ ] Loader and validation
  - [ ] Table-driven tests

#### E1-S3 · Member search and detail
Labels: `payer` `davinci` `backend` `frontend` · Size: M
- **AC**
  - [ ] `GET /api/members?name=&memberId=` and `GET /api/members/{id}` return DTOs (coverage, plan, prior auth history)
  - [ ] payer-app Members page: search table with sort and paginator, plus a detail view
- **Tests:** unit, web slice, authorization matrix, frontend unit, UI e2e "find a member"
- **Data:** uses the E1-S1 seed
- **Refs:** ADR 0002
- **Tasks**
  - [ ] Service + mapper + DTOs
  - [ ] Controller
  - [ ] payer-app pages

### E2 · CRD (Coverage Requirements Discovery)

#### E2-S1 · CDS Hooks discovery, order-sign and order-select
Labels: `payer` `davinci` `crd` `backend` · Size: L
- **AC**
  - [ ] `GET /cds-services` lists the services with prefetch templates (patient, coverage, practitioner)
  - [ ] `POST /cds-services/{id}` for `order-sign` and `order-select` returns cards, plus a `systemAction` that adds the CRD `coverage-information` extension to the draft order (covered / PA needed / doc needed, coverage assertion id, questionnaire link)
  - [ ] If prefetch is missing, the service fetches the data through the FHIR server named in the hook request
  - [ ] Answers come from `CoverageRuleService`; inactive coverage gives a "could not verify" card
  - [ ] Hook latency is recorded as a metric
- **Tests:** unit, interop server (HTTP against `/cds-services`), contract (golden hook request and response), conformance (Inferno CRD)
- **Data:** `fixtures/davinci/crd/order-sign-request.json`, `order-sign-response-pa-needed.json`, `order-sign-response-covered.json`
- **Refs:** ADR 0007, NFR-SEC-1, NFR-AUD-1, NFR-OBS-1, NFR-CONF-2
- **Tasks**
  - [ ] Discovery
  - [ ] Hook handler
  - [ ] Prefetch fallback
  - [ ] Card + systemAction builder

#### E2-S2 · appointment-book, encounter-start and order-dispatch hooks
Labels: `payer` `davinci` `crd` `backend` · Size: M
- **AC**
  - [ ] All three hooks are listed in discovery and return CRD-conformant responses
- **Tests:** unit, contract (golden request/response per hook)
- **Data:** golden fixtures per hook
- **Refs:** NFR-CONF-1
- **Tasks**
  - [ ] Handlers
  - [ ] Fixtures

#### E2-S3 · CRD client in clinic-api
Labels: `payer` `davinci` `crd` `backend` · Size: M
- **AC**
  - [ ] `ServiceRequestService.createServiceRequest` and appointment creation call `order-sign` / `appointment-book` with the draft resource and the patient's coverage
  - [ ] The returned `coverage-information` extension is stored on the `ServiceRequest`, and `ServiceRequestResponse` exposes `coverage` and `cards[]` as DTOs
  - [ ] 2 s timeout and fail open (NFR-REL-2): the order is saved with a "coverage unknown" card
  - [ ] A `system` token from payer-api is used and cached until it expires
- **Tests:** unit, HTTP client (`MockRestServiceServer`: success, timeout, 5xx), contract (request equals the golden file)
- **Data:** reuses the E2-S1 fixtures
- **Refs:** NFR-REL-2, ADR 0006
- **Tasks**
  - [ ] `CrdClient`
  - [ ] Token client
  - [ ] Wire into the services
  - [ ] DTO fields

#### E2-S4 · CRD cards in the clinician order dialog
Labels: `payer` `davinci` `crd` `frontend` · Size: M
- **AC**
  - [ ] After saving, the order dialog and the order list show a status badge (covered / PA needed / doc needed / unknown) and the card text
  - [ ] A "Complete documentation" action is shown when documentation is needed
- **Tests:** frontend unit, UI e2e "MRI order shows PA needed"
- **Data:** uses the §5 seed
- **Refs:** design system rules
- **Tasks**
  - [ ] Card component
  - [ ] Badge mapping
  - [ ] Specs

### E3 · DTR (Documentation Templates and Rules)

#### E3-S1 · Questionnaires and $questionnaire-package
Labels: `payer` `davinci` `dtr` `backend` · Size: M
- **AC**
  - [ ] DTR Questionnaires for MRI lumbar spine, polysomnography, continuous glucose monitor and echocardiogram are stored in the payer HAPI
  - [ ] `Questionnaire/$questionnaire-package` returns a bundle with the Questionnaire and its Libraries and ValueSets for a given order and coverage
- **Tests:** unit, interop server, contract, conformance (Inferno DTR, payer side)
- **Data:** `fixtures/davinci/dtr/*.json`; `payer-questionnaires.feature`
- **Refs:** NFR-CONF-1, NFR-CONF-2
- **Tasks**
  - [ ] Author the Questionnaires
  - [ ] Operation provider
  - [ ] Seed feature

#### E3-S2 · Prepopulation with FHIRPath
Labels: `payer` `davinci` `dtr` `backend` · Size: L
- **AC**
  - [ ] clinic-api evaluates SDC `initialExpression` and `x-fhir-query` variables with the HAPI FHIRPath engine against the provider HAPI
  - [ ] At least 60% of MRI lumbar spine items are prefilled from the seeded chart. Prefilled answers carry an origin flag.
  - [ ] An expression error leaves the item empty and is logged without PHI
- **Tests:** unit (one test per expression type), contract (prefilled `QuestionnaireResponse` matches the golden file)
- **Data:** `fixtures/davinci/dtr/qr-mri-prefilled.json`
- **Refs:** ADR 0005, NFR-SEC-6
- **Tasks**
  - [ ] Expression evaluator
  - [ ] Variable resolution
  - [ ] Origin flags

#### E3-S3 · DTR service and form API
Labels: `payer` `davinci` `dtr` `backend` · Size: M
- **AC**
  - [ ] `GET /api/service-requests/{id}/documentation` returns a form DTO (items, answers, enableWhen, prefilled flags)
  - [ ] `PUT .../documentation` saves an `in-progress` response; `POST .../documentation/complete` validates required items and saves it as `completed`
  - [ ] The `QuestionnaireResponse` is stored in the provider HAPI and linked to the order
- **Tests:** unit, web slice, authorization matrix
- **Data:** reuses the E3-S1 and E3-S2 fixtures
- **Refs:** ADR 0002
- **Tasks**
  - [ ] `DtrService`
  - [ ] Form mapper
  - [ ] Controller

#### E3-S4 · Questionnaire renderer in clinician-app
Labels: `payer` `davinci` `dtr` `frontend` · Size: L
- **AC**
  - [ ] Renders `group`, `display`, `string`, `text`, `boolean`, `integer`, `decimal`, `date`, `choice` and `open-choice` items with Material reactive forms, plus simple `enableWhen`
  - [ ] Prefilled answers are marked, required items are validated, and the form can be saved and completed
  - [ ] Keyboard accessible, with labels for every control
- **Tests:** frontend unit driven by the real `fixtures/davinci/dtr` Questionnaires, UI e2e "complete MRI documentation"
- **Data:** reuses the DTR fixtures
- **Refs:** design system rules
- **Tasks**
  - [ ] Item components
  - [ ] enableWhen
  - [ ] Save and complete flow
  - [ ] Specs

#### E3-S5 · CQL prepopulation (stretch)
Labels: `payer` `davinci` `dtr` `backend` · Size: L
- **AC**
  - [ ] CQL Libraries from the package are evaluated with `cqf-fhir-cr`, and the result matches the FHIRPath answers for the MRI form
- **Tests:** unit, contract
- **Data:** CQL Library fixture
- **Refs:** ADR 0005
- **Tasks**
  - [ ] Dependency spike
  - [ ] Evaluator
  - [ ] Parity test

### E4 · PAS (Prior Authorization Support) and UM review

#### E4-S1 · PAS request bundle builder
Labels: `payer` `davinci` `pas` `backend` · Size: M
- **AC**
  - [ ] clinic-api builds a PAS request bundle: `Claim` (use `preauthorization`, priority from the order), Patient, Coverage, Practitioner (NPI required), Organization, ServiceRequest, the completed QuestionnaireResponse and the coverage assertion id
  - [ ] `Claim.identifier` is stable for one order, so resubmits are idempotent (NFR-REL-1)
  - [ ] Missing NPI or incomplete documentation gives a 400 with a clear message
- **Tests:** unit, contract (bundle equals `fixtures/davinci/pas/request-mri.json`), profile validation
- **Data:** `fixtures/davinci/pas/request-*.json`
- **Refs:** NFR-REL-1, NFR-CONF-1
- **Tasks**
  - [ ] Builder
  - [ ] Validation
  - [ ] Golden test

#### E4-S2 · Claim/$submit
Labels: `payer` `davinci` `pas` `backend` · Size: L
- **AC**
  - [ ] Validates the bundle with `$validate` against the PAS profiles and returns an `OperationOutcome` on errors
  - [ ] Idempotent on `Claim.identifier`
  - [ ] Stores the Claim and adjudicates with `CoverageRuleService` and the QuestionnaireResponse answers
  - [ ] Returns a `ClaimResponse` bundle: approved, denied (with a reason, NFR-REG-2) or pended, with a `preAuthRef`
  - [ ] Sets the decision deadline (NFR-REG-1) and writes an `AuditEvent` (NFR-AUD-1)
- **Tests:** unit (adjudication table for every §5 outcome, fixed `Clock`), interop server, contract (golden responses), conformance (Inferno PAS)
- **Data:** `fixtures/davinci/pas/response-{approved,denied,pended}.json`
- **Refs:** ADR 0003, ADR 0004, ADR 0007, NFR-REG-1, NFR-REG-2, NFR-REL-1, NFR-AUD-1, NFR-CONF-2
- **Tasks**
  - [ ] Operation provider
  - [ ] Idempotency lookup
  - [ ] Adjudicator
  - [ ] Response builder
  - [ ] Audit

#### E4-S3 · Claim/$inquire
Labels: `payer` `davinci` `pas` `backend` · Size: S
- **AC**
  - [ ] Returns the current `ClaimResponse` for a matching Claim (identifier + patient + provider), limited by scope
- **Tests:** unit, interop server, authorization matrix
- **Data:** golden inquire request and response
- **Refs:** NFR-SEC-1, NFR-REL-3
- **Tasks**
  - [ ] Operation provider
  - [ ] Fixtures

#### E4-S4 · Decision notification to the clinic
Labels: `payer` `davinci` `pas` `backend` · Size: M
- **AC**
  - [ ] A rest-hook `Subscription` on `ClaimResponse` in the payer HAPI calls a clinic-api webhook with a shared secret (NFR-SEC-5), following the `AlertController` pattern
  - [ ] On notify, clinic-api calls `$inquire`, updates its stored prior auth and raises an Inbox item through the existing `Notifier` path
  - [ ] A scheduled `$inquire` fallback covers lost notifications (NFR-REL-3)
- **Tests:** unit, web slice (wrong secret gets 401), HTTP client
- **Data:** n/a
- **Refs:** ADR 0003, NFR-SEC-5, NFR-REL-3
- **Tasks**
  - [ ] Subscription bootstrap
  - [ ] Webhook
  - [ ] Inquire job
  - [ ] Notifier wiring

#### E4-S5 · UM review queue in payer-app
Labels: `payer` `davinci` `pas` `frontend` `backend` · Size: M
- **AC**
  - [ ] `GET /api/prior-auths?status=pended` returns queue items with the deadline and time remaining
  - [ ] payer-app Queue: sorted by deadline, expedited first, with a breach flag (NFR-REG-1)
- **Tests:** unit (SLA calculator with a fixed `Clock`), web slice, frontend unit (sorting), UI e2e "pended request appears in the queue"
- **Data:** Tony Stark's backdated request shows as breached
- **Refs:** NFR-REG-1
- **Tasks**
  - [ ] Queue endpoint
  - [ ] Queue page
  - [ ] Specs

#### E4-S6 · Review and decision
Labels: `payer` `davinci` `pas` `frontend` `backend` · Size: L
- **AC**
  - [ ] The review screen shows claim lines, the QuestionnaireResponse answers, attachments and which rules matched
  - [ ] Approve, deny (reason code + text required, NFR-REG-2) or pend. The decision updates the `ClaimResponse` and writes `Provenance` (NFR-AUD-2).
  - [ ] Only `UM_REVIEWER` can decide
- **Tests:** unit, web slice, authorization matrix, frontend unit, UI e2e "approve" and "deny without reason is blocked"
- **Data:** uses the §5 seed
- **Refs:** NFR-REG-2, NFR-AUD-2
- **Tasks**
  - [ ] Decision endpoint
  - [ ] Review page
  - [ ] Provenance

#### E4-S7 · Prior auth status for clinicians
Labels: `payer` `davinci` `pas` `frontend` `backend` · Size: M
- **AC**
  - [ ] `POST /api/prior-auths` (submit for an order) and `GET /api/prior-auths?patientId=` in clinic-api, with `priorauth:read` and `priorauth:write` permissions
  - [ ] clinician-app shows the prior auth status per order (submitted / pended / approved with auth number / denied with reason) and Inbox entries for decisions
- **Tests:** unit, web slice, authorization matrix, frontend unit, UI e2e
- **Data:** uses the §5 seed
- **Refs:** ADR 0002
- **Tasks**
  - [ ] Endpoints
  - [ ] Status panel
  - [ ] Inbox items

#### E4-S8 · Prior auth metrics
Labels: `payer` `davinci` `pas` `backend` `frontend` `observability` · Size: S
- **AC**
  - [ ] `GET /api/metrics/prior-auth?period=` returns % approved, denied and pended plus the median decision time (NFR-REG-3)
  - [ ] The payer-app Metrics page and the Prometheus counters agree
- **Tests:** unit, web slice, frontend unit
- **Data:** uses the §5 seed
- **Refs:** NFR-REG-3, NFR-OBS-1
- **Tasks**
  - [ ] Aggregation
  - [ ] Page
  - [ ] Counters

### E5 · CDex (attachments)

#### E5-S1 · Payer requests documents
Labels: `payer` `davinci` `cdex` `backend` `frontend` · Size: M
- **AC**
  - [ ] "Request documents" in the payer-app review screen sends a CDex attachment-request `Task` to a new clinic-api `interop` endpoint `/fhir/Task`, authenticated with a system token
  - [ ] clinic-api stores the Task and shows it in the clinician Inbox; the prior auth status becomes "documents requested"
- **Tests:** unit, interop server, authorization matrix, ArchUnit (the clinic-api `interop` package is the only FHIR-facing endpoint), UI e2e
- **Data:** `fixtures/davinci/cdex/task-request.json`
- **Refs:** ADR 0002, NFR-SEC-1, NFR-AUD-1
- **Tasks**
  - [ ] Task builder (payer side)
  - [ ] clinic-api interop endpoint
  - [ ] Inbox item

#### E5-S2 · Clinician attaches documents
Labels: `payer` `davinci` `cdex` `backend` `frontend` · Size: M
- **AC**
  - [ ] The clinician picks DiagnosticReports or notes. clinic-api sends them with `Claim/$submit-attachment`, and payer-api links them to the Claim and puts it back in the queue.
- **Tests:** unit, interop server, contract, UI e2e
- **Data:** `fixtures/davinci/cdex/submit-attachment.json`
- **Refs:** NFR-CONF-1
- **Tasks**
  - [ ] Attachment picker
  - [ ] Operation client
  - [ ] Operation provider

#### E5-S3 · Unsolicited attachments on submit
Labels: `payer` `davinci` `cdex` `backend` `frontend` · Size: S
- **AC**
  - [ ] Documents can be attached at prior auth submit time and appear on the review screen
- **Tests:** unit, contract
- **Data:** golden fixture
- **Refs:** NFR-CONF-1
- **Tasks**
  - [ ] Submit dialog option
  - [ ] Payload

### E6 · PDex (Payer Data Exchange)

#### E6-S1 · Patient Access API
Labels: `payer` `davinci` `pdex` `backend` `security` · Size: L
- **AC**
  - [ ] Read and search of Patient, Coverage, ExplanationOfBenefit (CARIN BB), prior auths (PDex prior authorization profile), Encounter, Condition, Observation and MedicationDispense
  - [ ] Results are limited to the member's compartment (NFR-SEC-2), and each access writes an `AuditEvent`
  - [ ] The CapabilityStatement lists the profiles and the SMART configuration
- **Tests:** unit, interop server, authorization matrix (member A vs member B), conformance (Inferno PDex and CARIN BB)
- **Data:** `fixtures/davinci/pdex/*.json`
- **Refs:** NFR-SEC-2, NFR-AUD-1, NFR-CONF-2
- **Tasks**
  - [ ] Resource providers
  - [ ] Compartment filter
  - [ ] CapabilityStatement

#### E6-S2 · Claims history seed
Labels: `payer` `davinci` `pdex` `testing` · Size: S
- **AC**
  - [ ] `payer-claims-history.feature` loads EOBs for the 8 members, reusing the `Claim` and `ExplanationOfBenefit` entries from the Synthea bundles in `clinic-api/seed-data`, remapped to the payer member ids
- **Tests:** seed-demo-data unit tests, a rerun idempotency check
- **Data:** this story is the data
- **Refs:** §5 data rules
- **Tasks**
  - [ ] Remapper
  - [ ] Feature

#### E6-S3 · "My insurance" in patient-app
Labels: `payer` `davinci` `pdex` `frontend` `backend` · Size: M
- **AC**
  - [ ] A new tab shows the coverage card, the prior auth list with statuses, and claims
  - [ ] clinic-api reads them from payer-api with the member's token; the patient app never calls payer-api directly
- **Tests:** unit, web slice, authorization matrix (patient self-access only), frontend unit, UI e2e
- **Data:** uses the E6-S2 seed
- **Refs:** NFR-SEC-2
- **Tasks**
  - [ ] Member token exchange
  - [ ] Endpoints
  - [ ] Tab UI

#### E6-S4 · Member attribution and Provider Access export
Labels: `payer` `davinci` `pdex` `backend` `security` · Size: L
- **AC**
  - [ ] An ATR `Group` per in-network provider organization
  - [ ] `Group/{id}/$export` goes through HAPI bulk export. payer-api checks attribution and opt-out (NFR-SEC-3) and proxies the kick-off and status endpoints.
- **Tests:** unit, interop server, authorization matrix (an opted-out member is excluded)
- **Data:** `payer-attribution.feature` with one opted-out member
- **Refs:** ADR 0003, NFR-SEC-3
- **Tasks**
  - [ ] Group seed
  - [ ] Export proxy
  - [ ] Opt-out filter

#### E6-S5 · Import payer data into the clinic
Labels: `payer` `davinci` `pdex` `backend` `frontend` · Size: M
- **AC**
  - [ ] An admin action in clinic-api starts a Provider Access export, polls it, and imports the NDJSON into the provider HAPI with `Provenance`
  - [ ] Importing the same data twice creates no duplicates
- **Tests:** unit, HTTP client, contract (NDJSON fixture)
- **Data:** `fixtures/davinci/pdex/export-*.ndjson`
- **Refs:** NFR-AUD-2
- **Tasks**
  - [ ] Export client
  - [ ] Importer
  - [ ] Admin UI

#### E6-S6 · Payer-to-Payer
Labels: `payer` `davinci` `pdex` `backend` `security` · Size: L
- **AC**
  - [ ] HRex `Patient/$member-match` takes the member Patient, old and new Coverage and a Consent, and returns the member identifier
  - [ ] No consent returns 422 (NFR-SEC-4), and a member-scoped `$export` follows the match
  - [ ] Demoed with a second payer organization as the "new payer"
- **Tests:** unit, interop server, contract, conformance (Inferno PDex payer-to-payer)
- **Data:** `fixtures/davinci/pdex/member-match-*.json`, second payer seed
- **Refs:** NFR-SEC-4, NFR-CONF-2
- **Tasks**
  - [ ] Matcher
  - [ ] Consent check
  - [ ] Export

### E7 · Plan-Net and Formulary

#### E7-S1 · Plan-Net directory API
Labels: `payer` `davinci` `plan-net` `backend` · Size: M
- **AC**
  - [ ] Public, unauthenticated read and search of Practitioner, PractitionerRole, Organization, Location, HealthcareService, InsurancePlan and Endpoint, with the Plan-Net search parameters
  - [ ] The directory includes the clinic's own practitioners
- **Tests:** interop server, conformance (Inferno Plan-Net)
- **Data:** `payer-directory.feature`, `fixtures/davinci/plan-net/*.json`
- **Refs:** NFR-CONF-2
- **Tasks**
  - [ ] Public providers
  - [ ] Seed

#### E7-S2 · Directory screens
Labels: `payer` `davinci` `plan-net` `frontend` · Size: M
- **AC**
  - [ ] clinician-app "Find in-network provider" (specialty + location) for referrals
  - [ ] payer-app read-only directory view
- **Tests:** frontend unit, UI e2e
- **Data:** uses the E7-S1 seed
- **Refs:** design system rules
- **Tasks**
  - [ ] clinic-api lookup endpoint
  - [ ] Both screens

#### E7-S3 · Formulary API
Labels: `payer` `davinci` `formulary` `backend` · Size: M
- **AC**
  - [ ] Public search of the formulary `InsurancePlan`, FormularyItem `Basic` and `MedicationKnowledge`, with the Formulary IG search parameters
- **Tests:** interop server, conformance (Inferno Formulary)
- **Data:** `payer-formulary.feature`, covering the drugs in the seed profiles
- **Refs:** NFR-CONF-2
- **Tasks**
  - [ ] Public providers
  - [ ] Seed

#### E7-S4 · Formulary lookup when recording medications
Labels: `payer` `davinci` `formulary` `frontend` `backend` · Size: S
- **AC**
  - [ ] The medication dialog shows the tier, PA flag and quantity limit for the patient's plan, using a clinic-api JSON endpoint
- **Tests:** unit, web slice, frontend unit
- **Data:** uses the E7-S3 seed
- **Refs:** ADR 0002
- **Tasks**
  - [ ] Endpoint
  - [ ] Dialog hint

### E8 · Conformance, end-to-end tests and docs

#### E8-S1 · Inferno test kits
Labels: `payer` `davinci` `testing` · Size: M
- **AC**
  - [ ] `infra/inferno/` runs the Da Vinci CRD, DTR, PAS, PDex, Plan-Net and Formulary test kits against payer-api, with a runbook
  - [ ] A nightly workflow runs them and uploads the reports. Each failure is either fixed or filed as an issue and listed in the test strategy.
- **Tests:** this story is the tests
- **Data:** n/a
- **Refs:** NFR-CONF-2
- **Tasks**
  - [ ] Compose file
  - [ ] Runbook
  - [ ] Nightly workflow

#### E8-S2 · Prior auth journey e2e
Labels: `payer` `davinci` `testing` · Size: L
- **AC**
  - [ ] One `@journey @payer` scenario runs the §3 journey across clinician-app, payer-app and patient-app, with shared steps in `libs/e2e-support`
  - [ ] It runs in `e2e.yml`
- **Tests:** this story is the test
- **Data:** the scenario creates its own patient and order
- **Refs:** §3
- **Tasks**
  - [ ] Feature file
  - [ ] Steps
  - [ ] CI

#### E8-S3 · Postman collections
Labels: `payer` `davinci` `testing` · Size: S
- **AC**
  - [ ] `payer-api/integration-tests/` has one collection per IG with example requests
- **Tests:** collections run green against the local stack
- **Data:** uses the seed
- **Refs:** n/a
- **Tasks**
  - [ ] Collections

#### E8-S4 · IG documentation
Labels: `payer` `davinci` `documentation` · Size: M
- **AC**
  - [ ] `docs/12-prior-auth-crd-dtr-pas.md`, `docs/13-payer-data-exchange-pdex.md`, `docs/14-directory-formulary.md`, `payer-api/openapi.yaml`
  - [ ] README, `CLAUDE.md` and `dev.sh` cover payer-api and payer-app
- **Tests:** n/a
- **Data:** n/a
- **Refs:** all ADRs
- **Tasks**
  - [ ] Docs
  - [ ] Scripts

#### E8-S5 · Payer dashboard and alerts
Labels: `payer` `davinci` `observability` · Size: S
- **AC**
  - [ ] Grafana gets a payer row (hook latency, decisions by outcome, decision time) and an alert for SLA breaches
- **Tests:** the dashboard JSON loads in the provisioned Grafana
- **Data:** n/a
- **Refs:** NFR-OBS-1
- **Tasks**
  - [ ] Panels
  - [ ] Alert rule

## 8. GitHub tracking

Each epic is a GitHub issue labelled `epic`, with its stories attached as sub-issues. Milestones M0–M8 match the epics. Issues copy the story text at creation time; if they drift, this file wins.

| Epic | Issue | Stories (sub-issues) |
|---|---|---|
| E0 | #56 | S1 #57, S2 #58, S3 #59, S4 #60, S5 #61, S6 #62, S7 #63, S8 #64, S9 #65 |
| E1 | #66 | S1 #67, S2 #68, S3 #69 |
| E2 | #70 | S1 #71, S2 #72, S3 #73, S4 #74 |
| E3 | #75 | S1 #76, S2 #77, S3 #78, S4 #79, S5 #80 |
| E4 | #81 | S1 #82, S2 #83, S3 #84, S4 #85, S5 #86, S6 #87, S7 #88, S8 #89 |
| E5 | #90 | S1 #91, S2 #92, S3 #93 |
| E6 | #94 | S1 #95, S2 #96, S3 #97, S4 #98, S5 #99, S6 #100 |
| E7 | #101 | S1 #102, S2 #103, S3 #104, S4 #105 |
| E8 | #106 | S1 #107, S2 #108, S3 #109, S4 #110, S5 #111 |
