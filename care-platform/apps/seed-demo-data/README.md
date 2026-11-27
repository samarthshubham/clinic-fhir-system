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
19 scenarios (19 passed)
228 steps (228 passed)
```

An HTML report is written to `apps/seed-demo-data/reports/seed-data-report.html` after each run.

> **Idempotency**: Each run is safe to repeat. Before creating a patient, the step cascade-deletes that patient's existing observations and encounters, then re-creates everything with the same fixed ID. Practitioner and organization follow the same pattern. Running the seed twice produces identical data — no duplicates.

### Staff practitioners (`staff-practitioners.feature`)

The Practitioners behind the dev staff logins `admin`, `clinician` and `frontdesk` (`user-admin`, `user-clinician`, `user-frontdesk`), PUT by id with the same names, title and `http://praxis.clinic/fhir/identifier/username` identifier that clinic-api's `StaffAccountBootstrap` writes at startup. A rerun, or a clinic-api restart, leaves them unchanged. After a provider HAPI reset the seed restores them, so the logins get their `practitioner_id` back without a clinic-api restart (saving DTR documentation needs it).

### Payer data (`src/features/payer/`)

`cucumber.json` runs the payer features after the patient profiles: plans, members, terminology, questionnaires, directory, then prior authorizations:

| Feature                        | Seeds                                                                                                                                                                                                                                                                               |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `payer-plans.feature`          | Praxis Health Plan (`Organization`) and two `InsurancePlan`s, Praxis Gold PPO and Praxis Silver HMO, in the payer HAPI                                                                                                                                                              |
| `payer-members.feature`        | for each of the 8 profiles: the provider `Coverage` (through clinic-api), and a member `Patient` + `Coverage` in the payer HAPI. Aisha Chen's payer `Coverage` is `cancelled` while her provider `Coverage` stays `active`, so CRD answers "Coverage could not be verified" for her |
| `payer-terminology.feature`    | CPT, HCPCS, ICD-10-CM, X12 and NUCC provider taxonomy demo fragments, plus complete THO and DTR CodeSystems from `terminology/codesystems/`                                                                                                                                         |
| `payer-questionnaires.feature` | the DTR `Questionnaire`s, CQL `Library`s and `ValueSet`s from `fixtures/davinci/dtr/` (MRI lumbar spine, polysomnography, CGM, echocardiogram), PUT under their fixed ids; then checks that payer-api's `$questionnaire-package` returns the right form for each order code         |
| `payer-directory.feature`      | Plan-Net directory fixtures from `fixtures/davinci/plan-net/`, including the clinic’s two seeded clinicians. Updates the existing payer and plans in place and verifies public directory searches.                                                                                  |
| `payer-prior-auths.feature` | Tony Stark’s cardiac stress test request, backdated eight days and submitted through PAS `Claim/$submit`. The seed checks for the pended decision and reuses it on subsequent seeds. |

The complete snapshots preserve the concepts from `hl7.terminology.r4#7.1.0` (claim type and CMS place of service) and `hl7.fhir.us.davinci-dtr#2.2.0` (information origin, completion time and intended audience). For a new or changed complete system, the seed writes its published concepts to the matching CodeSystem resource and verifies each code with versioned `CodeSystem/$lookup` before recording the source digest. For fragment imports it checks every final batch entry, then waits until each demo code resolves without a version, as PAS submits it. [HAPI 8.12 terminology uploader](https://github.com/hapifhir/hapi-fhir/blob/v8.12.0/hapi-fhir-jpaserver-base/src/main/java/ca/uhn/fhir/jpa/provider/TerminologyUploaderProvider.java)

- Both `Coverage`s of a member carry the same identifier, `http://praxishealthplan.example/fhir/identifier/member-id|PHP-#####`. That's how the two sides match a member. The payer `Patient` copies its demographics from the provider `Patient`.
- Payer member, coverage and plan creation uses identifier-based conditional creates (`If-None-Exist`). The PAS seed checks the existing decision by the stable Claim identifier before submitting, preserving the original decision deadline.
- The profile features rebuild their provider patients on every run, which deletes the provider `Coverage`. `payer-members.feature` runs after them and creates it again.
- Payer dates are set by the first run and stay fixed after that. The provider `Coverage` reuses the payer `Coverage`'s start date (and its end date, when the provider row has one), so both sides keep the same period on every rerun.
- Each payer scenario first gets a payer-api system token (`client_credentials`) and keeps it on the world as `payerApiToken`, for payer stories that will call payer-api. The defaults are the dev client `clinic-api` / `dev-only-clinic-api-client-secret` from payer-api's `application-dev.yaml`. Override them with `PAYER_CLIENT_ID` / `PAYER_CLIENT_SECRET`, and the URLs with `PAYER_FHIR_URL` (default `http://localhost:8081/fhir`) and `PAYER_API_URL` (default `http://localhost:9095`).

---

## Patient profiles

| Patient        | DOB          | Profile                                                                   | Encounters                                                                                 | Key vitals trend                          |
| -------------- | ------------ | ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ | ----------------------------------------- |
| Marcus Webb    | 1965-03-12 M | Hypertensive, ongoing BP management                                       | 4 (42, 28, 14, 3 days ago)                                                                 | Systolic 158 → 138 mmHg; no SpO₂ recorded |
| Priya Nair     | 1992-07-28 F | Healthy, preventive care                                                  | 2 (38, 10 days ago)                                                                        | All normal; SpO₂ 99%                      |
| Gerald Horton  | 1974-11-04 M | Overweight, sleep apnea workup + CPAP                                     | 4 (45, 30, 15, 2 days ago)                                                                 | SpO₂ 93% → 97%; weight 118 → 115 kg       |
| Sandra Okafor  | 1980-02-19 F | Post-cholecystectomy recovery; lumbar radiculopathy (MRI prior auth case) | 9 (44, 35, 21, 7 days ago; back pain 34 days ago, physical therapy 30, 23, 16, 6 days ago) | All vitals normalizing; weight 72 → 69 kg |
| Ramon Castillo | 1958-09-30 M | Type 2 diabetes management                                                | 4 (40, 25, 12, 1 days ago)                                                                 | BP 136/86 → 128/80; SpO₂ stable 96-97%    |

The profiles are chosen to exercise a range of UI states:

- **Marcus** — no SpO₂ data; tests optional-vital handling in charts
- **Priya** — only 2 encounters; tests sparse data rendering
- **Gerald** — SpO₂ starts at 93%; tests critical/warning threshold display
- **Sandra** — back pain chart for DTR prepopulation of the MRI lumbar spine form: active lumbar radiculopathy (onset 42 days ago), naproxen, a lumbar spine X-ray report and 4 physical therapy visits, so 4 weeks of conservative therapy, under the payer's 6 weeks
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

| Data table key | API field     | Unit |
| -------------- | ------------- | ---- |
| `systolicBp`   | `systolicBp`  | mmHg |
| `diastolicBp`  | `diastolicBp` | mmHg |
| `weightKg`     | `weightKg`    | kg   |
| `spo2Percent`  | `spo2Percent` | %    |

### Add new step patterns

If you need a step that doesn't exist yet, add it to the appropriate file in `src/step-definitions/`. Use the `function` keyword (never arrow functions) so Cucumber can bind `this` to `SeedWorld`:

```typescript
When('some new step {string}', async function (this: SeedWorld, value: string) {
  // this.currentPatientId is available here
});
```

---

## Technical notes

- **`@swc-node/register`** is used as the TypeScript loader (already installed in the monorepo as part of Nx). No separate `ts-node` installation is needed.
- **`module: "commonjs"`** in `tsconfig.json` overrides the monorepo base (`"esnext"`) — this is required for Cucumber's synchronous `require()` step loading to work.
- **HAPI FHIR vs Spring Boot split**: Practitioners, Organizations, and Patients are PUT directly to HAPI FHIR (port 8080) using fixed seed IDs — these resources have no Spring Boot endpoints, and fixed IDs make cascade delete reliable. Encounters and vitals go through the Spring Boot API (port 9090) as normal, with HAPI assigning their UUIDs.
- **Content-Type**: Spring Boot endpoints accept `application/json`; HAPI FHIR direct calls require `application/fhir+json`.
