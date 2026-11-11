# seed-demo-data

A Cucumber-driven seed data app for the clinic-fhir-system, written in TypeScript. It populates the local development stack with five realistic patient profiles — each with encounters and vitals spread over the past ~45 days — so the clinician UI always has meaningful, recent data to render.

---

## Why this exists

The original seed data consisted of large Synthea-generated FHIR bundles posted directly to HAPI FHIR. Those bundles:

- Had hardcoded absolute dates that aged out quickly
- Were opaque — hard to read, impossible to tweak without regenerating
- Included hundreds of irrelevant FHIR resource types (conditions, medications, imaging, etc.)

This app replaces that with **Gherkin feature files** that describe exactly what data exists and why. Every run recalculates dates relative to today, so the UI always sees fresh, in-range readings.

---

## How it works

```
patients.feature  (Gherkin scenarios with data tables)
       ↓
Step definitions  (TypeScript — background, patient, encounter, vitals)
       ↓
api-client.ts     (typed fetch wrappers)
       ↓
HAPI FHIR direct (port 8080)  →  Practitioner, Organization, Patient (PUT with fixed ID)
Spring Boot API  (port 9090)  →  Encounter, Vitals (POST — HAPI assigns UUID)
```

Each Cucumber `Scenario` is one patient. Before creating a patient, the step cascade-deletes all existing observations and encounters for that patient's fixed ID, then PUTs the patient resource directly to HAPI FHIR — ensuring the same ID is used on every run. Encounters and vitals are created through the Spring Boot API as usual. The `SeedWorld` class carries the `currentPatientId` and `currentEncounterId` between steps — state never bleeds between patients.

---

## Prerequisites

All of these must be running before executing the seed:

**1. Provider HAPI FHIR (8080), payer HAPI FHIR (8081) + PostgreSQL** (Docker):
```bash
# from project root
docker compose -f infra/docker-compose.yml up -d
bash infra/payer/smoke.sh   # waits until the payer HAPI has loaded its IGs (4-5 min on first start)
```

**2. Spring Boot API** (port 9090):
```bash
# from clinic-api/
./mvnw spring-boot:run
```

**3. payer-api** (port 9095), for the `payer-*` features:
```bash
# from payer-api/
./mvnw spring-boot:run
```

---

## Running the seed

```bash
# from care-platform/
npx nx run seed-demo-data:seed
```

The Spring Boot API requires a JWT, so a Cucumber `BeforeAll` hook (`src/support/hooks.ts`) logs in once before any scenario runs. It uses the dev `clinician` account by default; override it with `SEED_USERNAME` / `SEED_PASSWORD`. The account needs clinical write permissions (`CLINICIAN` or `ADMIN`). Calls that go straight to HAPI FHIR (patients, practitioners, organizations, cascade deletes) are not affected.

Expected output on success:
```
17 scenarios (17 passed)
212 steps (212 passed)
```

An HTML report is written to `apps/seed-demo-data/reports/seed-data-report.html` after each run.

> **Idempotency**: Each run is safe to repeat. Before creating a patient, the step cascade-deletes that patient's existing observations and encounters, then re-creates everything with the same fixed ID. Practitioner and organization follow the same pattern. Running the seed twice produces identical data — no duplicates.

### Payer data (`src/features/payer/`)

`cucumber.json` runs the payer features after the patient profiles, plans before members:

| Feature | Seeds |
|---|---|
| `payer-plans.feature` | Praxis Health Plan (`Organization`) and two `InsurancePlan`s, Praxis Gold PPO and Praxis Silver HMO, in the payer HAPI |
| `payer-members.feature` | for each of the 8 profiles: the provider `Coverage` (through clinic-api), and a member `Patient` + `Coverage` in the payer HAPI. Aisha Chen's coverage is `cancelled` |
| `payer-questionnaires.feature` | the DTR `Questionnaire`s, CQL `Library`s and `ValueSet`s from `fixtures/davinci/dtr/` (MRI lumbar spine, polysomnography, CGM, echocardiogram), PUT under their fixed ids; then checks that payer-api's `$questionnaire-package` returns the right form for each order code |

- Both `Coverage`s of a member carry the same identifier, `http://praxishealthplan.example/fhir/identifier/member-id|PHP-#####`. That's how the two sides match a member. The payer `Patient` copies its demographics from the provider `Patient`.
- payer-api has no write endpoints yet, so payer resources go straight to the payer HAPI. They are identifier-based conditional creates (`If-None-Exist`), so a rerun leaves them as they are: no new resources and no new versions.
- The profile features rebuild their provider patients on every run, which deletes the provider `Coverage`. `payer-members.feature` runs after them and creates it again.
- Payer dates are set by the first run and stay fixed after that. Provider dates are recalculated on every run, so the payer and provider coverage periods drift apart by the days since the first seed. Wipe the payer HAPI (`docker compose down -v`) to realign them.
- Each payer scenario first gets a payer-api system token (`client_credentials`) and keeps it on the world as `payerApiToken`, for payer stories that will call payer-api. The defaults are the dev client `clinic-api` / `dev-only-clinic-api-client-secret` from payer-api's `application-dev.yaml`. Override them with `PAYER_CLIENT_ID` / `PAYER_CLIENT_SECRET`, and the URLs with `PAYER_FHIR_URL` (default `http://localhost:8081/fhir`) and `PAYER_API_URL` (default `http://localhost:9095`).

---

## Patient profiles

| Patient | DOB | Profile | Encounters | Key vitals trend |
|---|---|---|---|---|
| Marcus Webb | 1965-03-12 M | Hypertensive, ongoing BP management | 4 (42, 28, 14, 3 days ago) | Systolic 158 → 138 mmHg; no SpO₂ recorded |
| Priya Nair | 1992-07-28 F | Healthy, preventive care | 2 (38, 10 days ago) | All normal; SpO₂ 99% |
| Gerald Horton | 1974-11-04 M | Overweight, sleep apnea workup + CPAP | 4 (45, 30, 15, 2 days ago) | SpO₂ 93% → 97%; weight 118 → 115 kg |
| Sandra Okafor | 1980-02-19 F | Post-cholecystectomy recovery | 4 (44, 35, 21, 7 days ago) | All vitals normalizing; weight 72 → 69 kg |
| Ramon Castillo | 1958-09-30 M | Type 2 diabetes management | 4 (40, 25, 12, 1 days ago) | BP 136/86 → 128/80; SpO₂ stable 96-97% |

The profiles are chosen to exercise a range of UI states:

- **Marcus** — no SpO₂ data; tests optional-vital handling in charts
- **Priya** — only 2 encounters; tests sparse data rendering
- **Gerald** — SpO₂ starts at 93%; tests critical/warning threshold display
- **Ages 34–68** — exercises age and gender display across the roster

---

## File structure

```
apps/seed-demo-data/
├── cucumber.json               Cucumber configuration (requireModule, paths, format)
├── project.json                Nx project — exposes the `seed` target
├── tsconfig.json               CommonJS override (required for Cucumber's require() loader)
├── reports/
│   └── seed-data-report.html   Generated after each run (gitignored)
└── src/
    ├── features/
    │   └── patients.feature    Gherkin scenarios — the single source of truth for seed data
    ├── step-definitions/
    │   ├── background.steps.ts Deletes + re-creates Practitioner and Organization via HAPI FHIR PUT
    │   ├── patient.steps.ts    Cascade-deletes by fixed ID, then PUTs Patient to HAPI FHIR
    │   ├── encounter.steps.ts  Records an encounter via POST /api/encounters (Spring Boot)
    │   └── vitals.steps.ts     Records vitals via POST /api/vitals (Spring Boot)
    └── support/
        ├── world.ts            SeedWorld — shared state (patientId, encounterId) per scenario
        ├── api-client.ts       Typed fetch wrappers for Spring Boot (9090) and HAPI FHIR (8080)
        └── date-helpers.ts     daysAgo(n) → ISO date string relative to today
```

---

## Extending the seed data

### Add a new patient

Add a new `Scenario` block to `src/features/patients.feature`. The step patterns are already defined — just fill in the data tables:

```gherkin
Scenario: Seed asthma patient — Ada Chen
  Given a patient exists with the following details:
    | id          | seed-ada-chen         |
    | firstName   | Ada                   |
    | lastName    | Chen                  |
    | dateOfBirth | 1988-04-20            |
    | gender      | female                |
    | phone       | 5551006006            |
    | email       | ada.chen@example.com  |

  When an encounter is recorded 20 days ago with reason "Asthma review" and status "finished"
  And vitals are recorded for that encounter:
    | systolicBp  | 118 |
    | diastolicBp | 75  |
    | weightKg    | 62  |
    | spo2Percent | 95  |
```

All four vital fields are optional — omit any that weren't recorded for a given visit.

### Add a new vital type to an existing encounter

Add the key to the data table for that encounter step. The keys map directly to the `VitalsRequest` fields accepted by the Spring Boot API:

| Data table key | API field | Unit |
|---|---|---|
| `systolicBp` | `systolicBp` | mmHg |
| `diastolicBp` | `diastolicBp` | mmHg |
| `weightKg` | `weightKg` | kg |
| `spo2Percent` | `spo2Percent` | % |

### Add new step patterns

If you need a step that doesn't exist yet, add it to the appropriate file in `src/step-definitions/`. Use the `function` keyword (never arrow functions) so Cucumber can bind `this` to `SeedWorld`:

```typescript
When('some new step {string}', async function(this: SeedWorld, value: string) {
  // this.currentPatientId is available here
});
```

---

## Technical notes

- **`@swc-node/register`** is used as the TypeScript loader (already installed in the monorepo as part of Nx). No separate `ts-node` installation is needed.
- **`module: "commonjs"`** in `tsconfig.json` overrides the monorepo base (`"esnext"`) — this is required for Cucumber's synchronous `require()` step loading to work.
- **HAPI FHIR vs Spring Boot split**: Practitioners, Organizations, and Patients are PUT directly to HAPI FHIR (port 8080) using fixed seed IDs — these resources have no Spring Boot endpoints, and fixed IDs make cascade delete reliable. Encounters and vitals go through the Spring Boot API (port 9090) as normal, with HAPI assigning their UUIDs.
- **Content-Type**: Spring Boot endpoints accept `application/json`; HAPI FHIR direct calls require `application/fhir+json`.
