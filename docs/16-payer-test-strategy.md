# Payer test strategy

How the payer and Da Vinci work is tested, and where test data comes from. Every roadmap story lists the test levels it needs ("Tests:" in [the roadmap](roadmap/payer-davinci.md)). The `roadmap-review` check fails a PR when a required level is missing.

## 1. Test levels

| Level | Where | What it proves | Tooling |
|---|---|---|---|
| **unit** | payer-api, clinic-api | rules, adjudication, the SLA clock, scope checks, mappers, bundle builders | JUnit 5, Mockito |
| **web slice** | both APIs | JSON endpoints, `@PreAuthorize`, the `{status, message}` error shape | `@WebMvcTest` + `@Import(SecurityConfig.class)` + `@WithRole` |
| **authorization matrix** | both APIs | every endpoint × role, plus payer scopes, member compartment, opt-out and consent | `AuthorizationMatrixTest` |
| **ArchUnit** | both APIs | FHIR types only where ADR 0002 allows them | ArchUnit |
| **interop server** | payer-api | FHIR operations (`$submit`, `$inquire`, `$questionnaire-package`, `$member-match`, `$submit-attachment`) and CDS Hooks over real HTTP | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + HAPI `IGenericClient`; the payer HAPI client is mocked |
| **HTTP client** | clinic-api | CRD timeout and fail-open, PAS retries, the `$inquire` fallback | Spring `MockRestServiceServer` |
| **contract** | both | both sides agree on the wire format: clinic-api's output equals the golden file, and payer-api accepts it | shared golden fixtures (§2) |
| **profile validation** | CI job `ig-validate` (`.github/scripts/ig-validate.sh`) | golden fixtures and mapper output (`target/fhir-out/`, uploaded by the `backend` and `payer-api` jobs) conform to US Core and the Da Vinci profiles | HL7 `validator_cli.jar`, pinned by version and SHA-256, with the `-ig` packages from `infra/docker-compose.yml` and tx.fhir.org |
| **frontend unit** | each app | the Questionnaire renderer (driven by real fixtures), CRD cards, queue sorting, prior auth status | Vitest, 90% line floor |
| **API e2e** | `payer-app-e2e` `@api` | payer-api against the running stack (`e2e.yml` starts both HAPI servers, both APIs and all three e2e suites) | Cucumber + `libs/e2e-support` |
| **UI e2e** | `@ui` in each e2e app | screens behave correctly | Cypress + Cucumber |
| **journey e2e** | `@journey @payer` | the end-to-end goal in roadmap §3, across three apps and two APIs | Cypress + Cucumber |
| **conformance** | `infra/inferno/` + nightly workflow | IG conformance for CRD, DTR, PAS, PDex, Plan-Net and Formulary | Inferno Da Vinci test kits |

The existing gates still apply, extended to payer-api and payer-app:

- 90% line coverage per module.
- 70% coverage of changed lines (`diff-cover` in `ci.yml`).
- Spotless and Prettier.

## 2. Test data tiers

1. **Unit fixtures.** Small hand-written JSON next to the test that uses it.
2. **Golden fixtures** in `fixtures/davinci/{provider,crd,dtr,pas,cdex,pdex,plan-net,formulary}/`.
   - They start from the official IG examples and are adapted to the demo identities.
   - Both Maven modules load them as test resources. clinic-api tests check that what it *builds* equals the golden file; payer-api tests check that it *accepts* the golden file and answers with the golden response.
   - The `ig-validate` job validates every file.
   - Changing a golden file is a contract change, and the PR has to say so.
3. **Demo seed** in `apps/seed-demo-data`: idempotent Gherkin features.

   | Feature | Seeds |
   |---|---|
   | `payer-plans.feature` | Praxis Health Plan, two `InsurancePlan`s |
   | `payer-members.feature` | member `Patient` + `Coverage` in the payer HAPI, matching `Coverage` in the provider HAPI |
   | `payer-questionnaires.feature` | DTR Questionnaires, Libraries and ValueSets from `fixtures/davinci/dtr/` |
   | `payer-claims-history.feature` | EOBs, remapped from the Synthea bundles in `clinic-api/seed-data` |
   | `payer-attribution.feature` | ATR `Group`s, one opted-out member |
   | `payer-directory.feature` | Plan-Net directory, including the clinic's practitioners |
   | `payer-formulary.feature` | formulary for the drugs in the seed profiles |

4. **Outcome matrix.** Each demo patient owns one prior auth outcome (roadmap §5), so every branch can be seeded and demoed: no PA, auto-approved, pended → docs → approved, expedited, denied with reason, SLA breached, emergency exempt, and inactive coverage.

The journey e2e creates its own patient and order, so it never depends on, or changes, the demo seed. `libs/e2e-support` cleanup removes what it created on both HAPI servers.

## 3. Rules for test data

- Fictional people and organizations only. Member ids `PHP-#####`; NPIs that pass the Luhn check but belong to nobody.
- Only a handful of CPT codes (AMA-licensed); prefer HCPCS, LOINC and SNOMED.
- Relative dates through `date-helpers.ts`, so the data never goes stale. Time-dependent logic (SLA deadlines) takes an injected `Clock` in tests.
- No real credentials. Demo accounts and client secrets live in dev-only config.

## 4. Known conformance gaps

Filled in from the Inferno runs (E8-S1). Each gap links to its issue.

| IG | Gap | Issue |
|---|---|---|
| DTR | CQL evaluation is not implemented until E3-S5 (ADR 0005) | _tbd_ |
