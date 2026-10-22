# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# clinic-fhir-system — Claude Context

A portfolio/learning project: a FHIR-compliant clinic management system built with Java 21, Spring Boot, HAPI FHIR, and Angular 21.

---

## Architecture

```
Angular (care-platform/)
    ↓ REST JSON  (proxy → localhost:9090)
clinic-api  (Spring Boot 3.5 / Java 21, port 9090)
    ↓ FHIR REST HTTP
HAPI FHIR JPA Server v7.4.0  (port 8080)
    ↓ JPA
PostgreSQL 15  (owned entirely by HAPI — Spring Boot never queries it directly)
```

**Spring Boot is not a pass-through proxy.** It owns all business logic, DTO validation, and DTO↔FHIR mapping. FHIR types never reach the HTTP client: every endpoint returns plain JSON DTOs.

---

## Repository Layout

```
clinic-api/         Spring Boot backend (Maven)
  src/main/java/io/github/samarthshubham/clinic/
    config/         FhirConfig, ClinicalThresholds, SecurityConfig, JwtConfig, AuthProperties
    security/       Role, Permission, RolePermissions, JsonSecurityErrorHandler
    controller/     PatientController, EncounterController, VitalsController,
                    PractitionerController, ConditionController, MedicationController,
                    AppointmentController, ServiceRequestController,
                    DiagnosticReportController, CarePlanController, GoalController,
                    DashboardController, AlertController, AuthController
    service/        PatientService, EncounterService, VitalsService,
                    PractitionerService, ConditionService, MedicationService,
                    AppointmentService, ServiceRequestService, DiagnosticReportService,
                    CarePlanService, GoalService,
                    SnapshotService, TrendsService, TimelineService,
                    DashboardService, ConditionEvaluationService,
                    HighBpAlertService, Notifier, LoggingSmsNotifier, FlagNotifier, AlertService,
                    AuthService, TokenService, StaffAccountBootstrap
    mapper/         PatientMapper, EncounterMapper, ObservationMapper,
                    PractitionerMapper, ConditionMapper, MedicationMapper,
                    AppointmentMapper, ServiceRequestMapper, DiagnosticReportMapper,
                    CarePlanMapper, GoalMapper, FlagMapper, PageMapper (search result → PageResponse)
    dto/            PatientRequest, EncounterRequest, VitalsRequest,
                    PractitionerRequest, ConditionRequest, MedicationRequest,
                    AppointmentRequest, ServiceRequestRequest, DiagnosticReportRequest,
                    CarePlanRequest, GoalRequest,
                    PatientResponse, EncounterResponse, ObservationResponse,
                    PractitionerResponse, ConditionResponse, MedicationResponse,
                    AppointmentResponse, ServiceRequestResponse, DiagnosticReportResponse,
                    CarePlanResponse, GoalResponse, PageResponse<T>,
                    SnapshotResponse, TrendsResponse, TimelineEvent, DashboardResponse,
                    AlertItem, VitalReading, LatestVitals, GoalProgress,
                    DataPoint, BpSeries, SimpleSeries, ClinicianAlert  (all Java records)
    exception/      ResourceNotFoundException, ReferenceValidationException,
                    GlobalExceptionHandler, ErrorResponse
  seed-data/        13 Synthea FHIR R4 patient bundles + load.sh
  integration-tests/ Postman collection

care-platform/      Angular 21 Nx monorepo
  apps/clinician-app/   Clinician-facing UI (active development)
  apps/patient-app/     Patient-facing UI (scaffolded, empty)
  apps/seed-demo-data/  Cucumber-driven idempotent seed data (8 patient profiles)
    src/features/       marcus-webb.feature, priya-nair.feature, gerald-horton.feature,
                        sandra-okafor.feature, ramon-castillo.feature, aisha-chen.feature,
                        robert-osei.feature, tony-stark.feature
    src/step-definitions/  background.steps.ts, patient.steps.ts, encounter.steps.ts,
                           vitals.steps.ts, clinical.steps.ts
    src/support/        api-client.ts, world.ts, date-helpers.ts
  libs/design-system/   SCSS tokens + palette + global utilities (planned)
  libs/ui/              Shared Angular components (planned)
  libs/shared/          ThemeService, FHIR model types (planned)

infra/
  docker-compose.yml    HAPI FHIR (rest-hook subscriptions on) + PostgreSQL 15

docs/
  00-architecture.md    System design
  01-module-design.md   Package structure + layer rules
  02-api-design.md      Full REST API reference with request/response shapes
  03-fhir-mapping.md    DTO→FHIR field mappings + LOINC codes
  04-key-flows.md       Sequence diagrams
  05-database.md        PostgreSQL internals (HAPI-owned)
  06-mvp-plan.md        MVP checklist
  07-best-practices.md  Spring + FHIR patterns
  08-design-system.md   Angular Material M3 design system spec
  09-bp-alerts-demo.md  High BP subscription alerts + scripted demo
  10-auth.md            JWT authentication + role → permission RBAC
```

---

## Serena (semantic code tools)

Use Serena tools when they are available in the session; otherwise use the standard Read/Grep/Glob/Edit tools. Serena is not required.

| Task | Serena tool | Do NOT use |
|---|---|---|
| Find a class or method | `find_symbol` | Grep |
| Understand a file's structure | `get_symbols_overview` | Read (whole file) |
| Replace a method/class body | `replace_symbol_body` | Edit / Write |
| Find callers of a function | `find_referencing_symbols` | Grep |
| Add code to end/start of file | `insert_after_symbol` / `insert_before_symbol` | Write |

When Serena is available, call `mcp__serena__initial_instructions` then `mcp__serena__check_onboarding_performed` before starting a code task. Its project memories are `code_style`, `project_overview`, `suggested_commands`, `tech_stack`.

---

## Development Commands

### Infrastructure (run from project root)
```bash
docker compose -f infra/docker-compose.yml up -d    # start HAPI FHIR + Postgres
docker compose -f infra/docker-compose.yml down     # stop (keep data)
docker compose -f infra/docker-compose.yml down -v  # stop + wipe data
```

### clinic-api (run from clinic-api/)
```bash
./mvnw spring-boot:run
./mvnw clean package
./mvnw test
./mvnw verify          # tests + JaCoCo report at target/site/jacoco/index.html
./mvnw spotless:apply   # format Java (Google style)
./mvnw spotless:check
```

### care-platform (run from care-platform/)
```bash
npx nx serve clinician-app    # dev server — proxies /api → localhost:9090
npx nx serve patient-app
npx nx build clinician-app
npx nx test clinician-app
npx nx test clinician-app --coverage --watch=false   # lcov + report in coverage/clinician-app/
```

### Seed data
```bash
bash clinic-api/seed-data/load.sh           # loads 13 Synthea patients into HAPI FHIR
npx nx run seed-demo-data:seed              # idempotent Cucumber seed — 8 realistic patients
```

---

## Commits

This is a personal project. Keep commits lightweight.

**Default to subject-only commits, imperative mood, with Conventional Commits prefix:**
- `feat: add dark mode toggle`
- `fix: off-by-one in pagination`
- `refactor: auth into hook`
- `chore: bump deps`
- `wip: trying new layout`

**Rules:**
- Commit when something works, or before trying something risky. Frequency over perfection.
- Never commit secrets (`.env`, API keys, tokens). Check `.gitignore` first.
- Never commit `node_modules/`, build output, `.DS_Store`, or other generated files.
- Add a one-line body only if I'd forget *why* in a week. Otherwise skip it.
- `wip:` prefix is fine for checkpoints.

**PR review:** the `claude-review` check runs the `incremental-review` skill (`.claude/skills/incremental-review/SKILL.md`) on every push. `.github/scripts/review-state.sh` tracks the last reviewed commit in a sticky PR comment, so only new non-merge commits are reviewed. Run it locally with `/incremental-review <pr-number>`.

**Slow down and confirm with me before:**
- Force-pushing or rewriting history.
- Destructive changes (migrations, deletions, `rm -rf` in scripts) — commit current state first as a safety net.
- Committing if you spot a secret already in the repo — flag it so I can rotate it.

**Don't add:**
- Co-author trailers or AI attribution.
- Long commit bodies explaining obvious diffs.
- Issue/ticket references.
- Any mention of gitignored files or changes — ignore them completely, don't stage or reference them.

---

## Backend Conventions (Java / Spring Boot)

- **DTOs** are Java `record` types. Use `@NotBlank`, `@NotNull` from `jakarta.validation`. Annotate controller params with `@Valid`.
- **Controllers** do HTTP routing only — `@RestController`, `@RequestMapping`, `ResponseEntity<T>`. No business logic.
- **Services** hold business logic. Constructor injection only (no `@Autowired` on fields). Inject `IGenericClient` + mapper.
- **Mappers** are `@Component` classes with `toFhir(request)` and `toResponse(resource)`. FHIR types (`org.hl7.fhir.r4.model.*`) are confined here and in services — **never in controllers or DTOs** (enforced by `ArchitectureTest`, ArchUnit).
- **Services** return response DTOs to controllers. When another service needs the FHIR resource, expose a separate method (e.g. `PatientService.findPatient` → `Patient`, `getPatient` → `PatientResponse`).
- **Config** uses `@Configuration` + `@Bean`. `IGenericClient` is thread-safe; one shared bean.
- **Error response shape**: `{ "status": int, "message": String }` — plain JSON, not FHIR.
- **`ResourceNotFoundException(resourceType, id)`** — throw when HAPI returns 404.
- **Logging**: `@Slf4j` (Lombok). Avoid `@Data` on mutable entities.
- **Formatting**: Google Java Format via Spotless. Run `./mvnw spotless:apply` before committing. Enforced by Husky pre-commit hook.
- **No comments** unless the WHY is non-obvious.

### Response format
All endpoints return plain JSON (`Content-Type: application/json`) via Spring's default Jackson converter. No FHIR resource is ever serialized to the client.
- **CRUD endpoints** return flat response records: `id`, the matching `*Request` fields, and a few read-only extras (e.g. `PatientResponse.active`). References are flattened to ids (`patientId`, `encounterId`, …). Missing fields are `null`. Dates come from the stored FHIR value, not the server's time zone.
  - `POST`, `GET /{id}` → single `XResponse`
  - `GET ?query` → `PageResponse<XResponse>` `{ "items": [...], "total": n }` (`items` = first page of HAPI results, `total` = HAPI's total; no paging yet)
  - `POST /api/vitals` → `ObservationResponse[]` (one per recorded vital)
- **Experience API endpoints** (`/snapshot`, `/trends`, `/timeline`, `/dashboard`) return aggregated DTOs.

---

## FHIR Mapping Rules

All resources use **FHIR R4**. FHIR server: `http://localhost:8080/fhir` (configured in `application.yaml` as `fhir.server.url`).

### Patient
| Request field | FHIR path |
|---|---|
| `firstName` + `lastName` | `Patient.name[0]` (HumanName) |
| `dateOfBirth` | `Patient.birthDate` |
| `gender` | `Patient.gender` (AdministrativeGender enum) |
| `phone` | `Patient.telecom[system=phone]` |
| `email` | `Patient.telecom[system=email]` |

### Encounter
| Request field | FHIR path |
|---|---|
| `patientId` | `Encounter.subject` → `Patient/{id}` |
| `visitDate` | `Encounter.period.start` |
| `reason` | `Encounter.reasonCode[0].text` |
| `status` | `Encounter.status` |

### Vitals — each vital is a **separate Observation** (FHIR standard)
| Vital | LOINC code | Unit |
|---|---|---|
| Systolic BP | `8480-6` | `mm[Hg]` |
| Diastolic BP | `8462-4` | `mm[Hg]` |
| Weight | `29463-7` | `kg` |
| SpO2 | `59408-5` | `%` |
| Heart rate | `8867-4` | `/min` |
| Temperature | `8310-5` | `Cel` |

LOINC system URI: `http://loinc.org`. Units system: `http://unitsofmeasure.org`. Status always `final`.

### Encounter (updated)
| Request field | FHIR path |
|---|---|
| `patientId` | `Encounter.subject` → `Patient/{id}` |
| `visitDate` | `Encounter.period.start` |
| `reason` | `Encounter.reasonCode[0].text` |
| `status` | `Encounter.status` |
| `practitionerId` *(optional)* | `Encounter.participant[type=ATND].individual` → `Practitioner/{id}` |
| `note` *(optional)* | `Encounter.extension[url=http://praxis.clinic/fhir/extension/encounter-note].valueString` |

### Condition
| Request field | FHIR path |
|---|---|
| `patientId` | `Condition.subject` |
| `encounterId` *(optional)* | `Condition.encounter` |
| `code` | `Condition.code.coding[0].code` |
| `display` | `Condition.code.text` |
| `clinicalStatus` | `Condition.clinicalStatus` (system: `http://terminology.hl7.org/CodeSystem/condition-clinical`) |
| `onsetDate` | `Condition.onsetDateTime` |

### MedicationStatement
| Request field | FHIR path |
|---|---|
| `patientId` | `MedicationStatement.subject` |
| `medicationName` | `MedicationStatement.medication[CodeableConcept].text` |
| `status` | `MedicationStatement.status` |
| `dosageText` | `MedicationStatement.dosage[0].text` |
| `startDate` | `MedicationStatement.effective[Period].start` |

### Appointment
| Request field | FHIR path |
|---|---|
| `patientId` | `Appointment.participant[actor=Patient]` |
| `practitionerId` *(optional)* | `Appointment.participant[actor=Practitioner]` |
| `start` / `end` | `Appointment.start` / `Appointment.end` |
| `description` | `Appointment.description` |
| `status` | `Appointment.status` |

### ServiceRequest
| Request field | FHIR path |
|---|---|
| `patientId` | `ServiceRequest.subject` |
| `encounterId` *(optional)* | `ServiceRequest.encounter` |
| `practitionerId` *(optional)* | `ServiceRequest.requester` |
| `code` | `ServiceRequest.code.text` |
| `category` | `ServiceRequest.category[0].text` |
| `status` | `ServiceRequest.status` (`active` → `completed` when DiagnosticReport is created) |
| `priority` | `ServiceRequest.priority` |
| `authoredOn` | `ServiceRequest.authoredOn` |

### DiagnosticReport
| Request field | FHIR path |
|---|---|
| `patientId` | `DiagnosticReport.subject` |
| `encounterId` *(optional)* | `DiagnosticReport.encounter` |
| `serviceRequestId` *(optional)* | `DiagnosticReport.basedOn[0]` — also auto-marks ServiceRequest `completed` |
| `title` | `DiagnosticReport.code.text` |
| `status` | `DiagnosticReport.status` |
| `issued` | `DiagnosticReport.issued` |
| `conclusion` | `DiagnosticReport.conclusion` |
| `resultIds[]` | `DiagnosticReport.result[]` → Observation references |

### CarePlan
| Request field | FHIR path |
|---|---|
| `patientId` | `CarePlan.subject` |
| `conditionIds[]` | `CarePlan.addresses[]` → Condition references |
| `title` | `CarePlan.title` |
| `status` | `CarePlan.status` |
| `periodStart` | `CarePlan.period.start` |
| `goalIds[]` | `CarePlan.goal[]` → Goal references |

### Goal
| Request field | FHIR path |
|---|---|
| `patientId` | `Goal.subject` |
| `description` | `Goal.description.text` |
| `status` | `Goal.lifecycleStatus` |
| `targetMeasureCode` | `Goal.target[0].measure.coding[0].code` (LOINC) |
| `targetMeasureDisplay` *(optional)* | `Goal.target[0].measure.coding[0].display` |
| `targetValue` | `Goal.target[0].detail[Quantity]` |
| `targetUnit` *(optional)* | `Goal.target[0].detail[Quantity].unit` / `.code` (UCUM) |
| `targetDate` | `Goal.target[0].due[DateType]` |

### Practitioner
| Request field | FHIR path |
|---|---|
| `firstName` + `lastName` | `Practitioner.name[0]` (HumanName) |
| `specialty` | `Practitioner.qualification[0].code.text` |
| `npi` *(optional)* | `Practitioner.identifier[system=NPI]` |
| `email` *(optional)* | `Practitioner.telecom[system=email]` |

### Alert thresholds (`ClinicalThresholds` config, `application.yaml`)
| Property | Default | Meaning |
|---|---|---|
| `clinical.thresholds.systolic-warn` | `140` | Systolic BP warning level |
| `clinical.thresholds.systolic-critical` | `180` | Systolic BP critical level |
| `clinical.thresholds.diastolic-warn` | `90` | Diastolic BP warning level |
| `clinical.thresholds.diastolic-critical` | `120` | Diastolic BP critical level |
| `clinical.thresholds.spo2-warn` | `95.0` | SpO2 warning level (below) |
| `clinical.thresholds.spo2-critical` | `90.0` | SpO2 critical level (below) |
| `clinic.default-org-id` | `seed-default-org` | HAPI FHIR Organization ID for care team |
| `clinic.alerts.webhook-url` | `http://host.docker.internal:9090/api/alerts/bp-subscription` | Rest-hook endpoint HAPI calls for critical systolic BP |
| `clinic.alerts.webhook-secret` | `${CLINIC_ALERTS_WEBHOOK_SECRET:dev-only-alert-secret}` | Shared secret HAPI sends as `X-Alert-Secret`; webhook returns 401 otherwise |
| `clinic.alerts.first-lookback` | `24h` | How far back the first notification after a restart searches (may re-alert) |

---

## REST API Reference

Base URL: `http://localhost:9090`

### Auth (public)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/login` | `{username, password}` → `TokenResponse` (access 15 min + refresh 7 days) |
| `POST` | `/api/auth/refresh` | `{refreshToken}` → new `TokenResponse` |

Every other `/api/**` endpoint needs `Authorization: Bearer <accessToken>` (except the alert webhook). Each controller method has `@PreAuthorize("hasAuthority('<permission>')")`; `RolePermissions` maps roles to permissions — see `docs/10-auth.md`. New endpoints must get a `@PreAuthorize` and a row in `AuthorizationMatrixTest`. `@WebMvcTest` controller tests use `@Import(SecurityConfig.class)` + `@WithRole(Role.ADMIN)`.

### CRUD — return response DTOs (`application/json`)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/patients` | Register patient → `PatientResponse` |
| `GET` | `/api/patients/{id}` | Get patient → `PatientResponse` |
| `GET` | `/api/patients?name=&dob=` | Search patients → `PageResponse<PatientResponse>` |
| `POST` | `/api/encounters` | Record visit (optional `practitionerId`) |
| `GET` | `/api/encounters/{id}` | Get encounter |
| `GET` | `/api/encounters?patientId={id}` | List encounters |
| `POST` | `/api/vitals` | Record vitals → `ObservationResponse[]` — auto-evaluates conditions on creation |
| `GET` | `/api/vitals?patientId={id}` | All vitals for patient |
| `GET` | `/api/vitals?patientId={id}&type=weight` | Filter by type (`systolic`, `diastolic`, `weight`, `spo2`) |
| `POST` | `/api/practitioners` | Register practitioner |
| `GET` | `/api/practitioners/{id}` | Get practitioner |
| `POST` | `/api/conditions` | Record diagnosis |
| `GET` | `/api/conditions/{id}` | Get condition |
| `GET` | `/api/conditions?patientId={id}` | List conditions |
| `POST` | `/api/medications` | Record medication |
| `GET` | `/api/medications?patientId={id}` | List medications |
| `POST` | `/api/appointments` | Schedule appointment |
| `GET` | `/api/appointments?patientId={id}` | List appointments |
| `POST` | `/api/service-requests` | Place lab/imaging order |
| `GET` | `/api/service-requests/{id}` | Get order |
| `GET` | `/api/service-requests?patientId={id}&status=active` | List orders (optional status filter) |
| `POST` | `/api/diagnostic-reports` | Create report (auto-completes linked ServiceRequest) |
| `GET` | `/api/diagnostic-reports?patientId={id}` | List reports |
| `POST` | `/api/care-plans` | Create care plan |
| `GET` | `/api/care-plans?patientId={id}` | List care plans |
| `POST` | `/api/goals` | Create goal |
| `GET` | `/api/goals?patientId={id}` | List goals |

### Experience APIs — return `application/json` (flat DTOs, not FHIR)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/dashboard/{patientId}` | Full clinical dashboard — partial-failure safe, `_warnings[]` on degraded sections |
| `GET` | `/api/patients/{id}/snapshot` | At-a-glance: active conditions, meds, latest vitals, alerts |
| `GET` | `/api/patients/{id}/trends?type=bp,spo2,weight&period=30d` | Chart-ready vitals series; `bp` returns paired systolic+diastolic |
| `GET` | `/api/patients/{id}/timeline?limit=20&before=2026-05-01&types=encounter,report` | Unified chronological event feed with cursor pagination |

### Alerts

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/alerts/bp-subscription` | HAPI rest-hook webhook (empty body, `X-Alert-Secret` header required) — alerts clinicians via every `Notifier` (mock SMS log + `FlagNotifier`) on systolic BP above `systolic-critical`; alerts carry only Patient/Observation references |
| `GET` | `/api/alerts?status=active` | Stored alerts (FHIR `Flag`) newest first → `PageResponse<AlertResponse>` (`alert:read`) |
| `POST` | `/api/alerts/{id}/acknowledge` | Mark an alert inactive → `AlertResponse` (`alert:write`) |

---

## Frontend Conventions (Angular / care-platform)

### Stack
- Angular 21, Angular Material M3 (`@angular/material ~21.2.0`)
- Nx monorepo (`npx nx` for all tasks)
- Prettier enforced via Husky pre-commit hook
- UI/UX design decisions use the `/ui-ux-pro-max` skill — invoke it for new screens, component design, layout, color, and accessibility work

### State management & data flow (RxJS-first)

**Rule:** All async operations use RxJS. `signal()` is only for synchronous UI state (toggle open/closed, active tab, density). Never use manual `.subscribe()` + `signal.set()` pairs for HTTP calls.

**The standard pattern:**
```typescript
// ✅ correct — RxJS stream → toSignal() at template boundary
readonly patients = toSignal(
  this.svc.getPatients().pipe(
    catchError(() => of(null)),
    startWith(null),           // null = skeleton state
  ),
);

// ❌ wrong — manual subscribe + signal pair
ngOnInit() {
  this.svc.getPatients().subscribe({
    next: p => this.patients.set(p),
    error: () => this.error.set(true),
  });
}
```

**Key operators and when to use them:**

| Operator | Use case |
|---|---|
| `switchMap` | User input changes a fetch (period picker, route params) — cancels prior in-flight request |
| `combineLatest` | Derived state from 2+ streams (filter + data list → filtered list) |
| `scan` | Append-only accumulation (infinite scroll page merging) |
| `debounceTime(250)` | Search inputs — wait for typing to pause before fetching |
| `distinctUntilChanged()` | Prevent duplicate HTTP calls when value hasn't changed |
| `shareReplay(1)` | Route param stream shared by multiple child streams without re-executing |
| `catchError(() => of(null))` | Per-stream error isolation — one panel failing doesn't block others |
| `startWith(null)` | Initial `null` emission drives skeleton loading state |
| `takeUntilDestroyed()` | Auto-cleanup on component destroy — no manual `ngOnDestroy` needed |
| `toSignal()` | Bridge Observable → template. Use `initialValue: null` for skeleton state |
| `toObservable()` | Bridge signal → stream when signal-owned state feeds a `switchMap` |

**What stays as plain `signal()`:** `expandedId`, `activeFilter`, `density`, `theme`, `isDragging` — anything that is synchronous, local, and never drives an HTTP call directly. If a signal feeds a `switchMap`, convert it with `toObservable()`.

**Subscription cleanup:** Use `takeUntilDestroyed()` from `@angular/core/rxjs-interop` for any `.subscribe()` that cannot be replaced with `toSignal()` (e.g. imperative Material DataSource wiring). Never implement `ngOnDestroy` just to unsubscribe.

### Two-app model
| | `clinician-app` | `patient-app` |
|---|---|---|
| Density | `-1` (compact) | `0` (comfortable) |
| Nav | `mat-sidenav` persistent sidebar | `mat-tab-nav-bar` bottom tabs |
| Data display | `mat-table` + `matSort` + `matPaginator` | `mat-card` stacked list |
| Forms | Reactive forms, validate `on blur` | `mat-stepper` linear flow |

### Design system rules
- **Never reimplement Angular Material components.** No custom button heights, input styles, or card borders via raw CSS.
- All overrides go through `mat.define-theme()` or Angular Material's supported CSS custom property API. Never target `.mdc-*` internal classes.
- SCSS tokens are `$cp-{category}-{modifier}` (e.g. `$cp-blue-500`, `$cp-space-4`). No raw hex in component SCSS — use tokens.
- CSS utility classes: `.cp-{purpose}` prefix only.
- Status badges: `.cp-status-badge--success/warning/critical/info` — used for inline chips only. Full notifications use `MatSnackBar`.
- Monospace class `.cp-font-mono` for vital readings, lab values, IDs.
- Angular component selectors: `cp-{name}`. Component classes: `Cp{Name}Component`.
- Nx library imports: `@care-platform/{lib}`.
- Dark mode via `[data-theme="dark"]` on `<html>` — toggled by `ThemeService`, respects `prefers-color-scheme`.

### Component styling pattern

**What belongs in a component `.scss` file:**
- Structural/layout styles only: flex, grid, positioning, width, height, padding, gap.
- Never colors, font families, or font sizes — those come from Material system tokens or global utilities.

**Colors — two token namespaces, clear rules on which to use:**

- **`var(--mat-sys-*)`** — use when overriding or extending an Angular Material component's own surface (e.g. a card's internal border, a drawer's background). These tokens are generated by `mat.define-theme()` and stay in sync with the M3 palette.
- **`var(--px-*)`** — use for everything outside Material components: layout panel backgrounds, card surfaces, borders, text colors, clinical accent colors, shadows. These are defined in the `--px-*` block in `styles.scss` and handle dark mode automatically.
- **Never use raw hex in component SCSS.** If neither token fits, add a new `--px-*` entry to `styles.scss`.

```scss
// correct — Material component surface
border-right: 1px solid var(--mat-sys-outline-variant);

// correct — layout / clinical surface (outside Material components)
background: var(--px-surface);
color: var(--px-text-muted);
border: 1px solid var(--px-border);

// wrong — raw hex
border-right: 1px solid #e2e8f0;
color: #334155;

// wrong — never do this
border-right: 1px solid #e2e8f0;
color: #334155;
```

**Global utilities — apply in templates, never redeclare in component SCSS:**
- `.cp-font-mono` — monospace for vitals, lab values, IDs
- `.cp-status-badge--{success|warning|critical|info}` — inline status chips
- `.cp-skip-link` — skip-to-content link
- `.cp-patient-content` — max-width container for patient app
- These are defined once in each app's `styles.scss`. Use them as HTML classes, don't copy into component files.

**`::ng-deep` — structural layout only:**
```scss
// permitted — reaching Material's generated wrapper for layout
::ng-deep .mat-drawer-content { display: flex !important; flex-direction: column; }

// not permitted — theming/color override
::ng-deep .mat-mdc-button { background: blue; }
```

**`!important` — Material specificity overrides only, with comment:**
```scss
// permitted — overcoming Material's component specificity for active state
:host ::ng-deep .nav-item--active {
  background: var(--mat-sys-secondary-container) !important; // needs !important to override mat-list-item base
}
```

**Dark mode — never add manually in component SCSS:**
- Handled automatically by `[data-theme='dark']` + `mat.all-component-themes()` in `styles.scss`.
- If you use `var(--mat-sys-*)` tokens, dark mode works for free.

**BEM naming:**
- App-specific: `.app-{block}`, `.app-{block}__{element}`, `.app-{block}--{modifier}`
- Shared lib components: `.cp-{name}` prefix

**Spacing — 4px grid, no arbitrary values:**
- Use: 4, 8, 12, 16, 24, 32, 48, 64 px
- Will be replaced by `$cp-space-*` tokens when `libs/design-system` is created

### Status
- **clinician-app**: M3 shell + theme bootstrapped; patient roster and patient detail page implemented.
- **patient-app**: Scaffolded, no feature code yet.
- **libs/design-system, libs/ui, libs/shared**: Defined in `docs/08-design-system.md` but not yet created on disk.

---

## Project Status (as of 2026-05-02)

**Backend (clinic-api):** Production-style clinical dashboard backend complete. 11 FHIR resource types, 4 experience APIs, clinical intelligence layer (auto-condition detection, goal progress evaluation), partial-failure-safe dashboard aggregation.

**Frontend (care-platform):** Active revamp in progress — see [`revamp/`](revamp/README.md) for the full plan. The current `clinician-app` (patient roster + legacy patient-detail with ApexCharts) is being replaced by the **Praxis** 3-panel clinical dashboard. Do not add features to the existing `patient-detail` component — it will be removed at the end of the revamp.

**Seed data:** `apps/seed-demo-data` — 8 idempotent patient profiles, each in its own `.feature` file.

**Active work (revamp phases):**
- Pre-Phase 0: Refactor `PatientRosterComponent` to RxJS (`toSignal`, `combineLatest`, `debounceTime`)
- Pre-Phase 1: `--px-*` token layer + Inter font + teal M3 theme
- Phase 1: `cp-topbar`, `cp-patient-header`, `DashboardComponent` shell (parallel RxJS streams)
- Phase 2: `cp-snapshot-panel` — vitals, alerts, conditions, medications
- Phase 3: `cp-timeline-panel` — inline expand, sparklines, infinite scroll
- Phase 4: `cp-care-panel` — goals, pending orders, SVG trend charts, draggable resizer

**Backend gaps to close (required before Phase 3):**
- `TimelineEvent`: add `chiefComplaint`, `note`, `groupedObservations`, `linkedTo`/`linkedFrom`
- `GoalProgress`: add `baselineValue`
- `LatestVitals`: add `heartRate`, `temperature` (non-blocking, tiles show "–" until added)

**Post-revamp:** CORS config, remove ApexCharts from `package.json`

---

## Key Rules (do not violate)

1. FHIR types (`org.hl7.fhir.*`) never appear in controllers or DTOs. `ArchitectureTest` (ArchUnit) fails `./mvnw verify` otherwise.
2. Spring Boot never queries PostgreSQL directly — all data access goes through HAPI FHIR REST.
3. Do not reimplement Angular Material components with custom SCSS.
4. One shared `IGenericClient` bean (thread-safe). `IParser` instances are per-request (not thread-safe).
5. All request bodies annotated with `@Valid` at the controller.
6. Error responses are `{ "status": int, "message": String }` plain JSON, not FHIR OperationOutcome.
