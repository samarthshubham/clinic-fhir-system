# MVP Plan

Target: 1–2 weeks. Single Spring Boot monolith. No microservices yet.

---

## Phase 1 — Foundation (Days 1–2)

- [x] Create Spring Boot project via [start.spring.io](https://start.spring.io)
  - Dependencies: `spring-web`, `spring-boot-starter-validation`, `lombok`
- [x] Add HAPI FHIR dependencies to `pom.xml`
  ```xml
  <dependency>
      <groupId>ca.uhn.hapi.fhir</groupId>
      <artifactId>hapi-fhir-client</artifactId>
      <version>7.4.0</version>
  </dependency>
  <dependency>
      <groupId>ca.uhn.hapi.fhir</groupId>
      <artifactId>hapi-fhir-structures-r4</artifactId>
      <version>7.4.0</version>
  </dependency>
  ```
- [x] Run HAPI FHIR + PostgreSQL via `infra/docker-compose.yml`
- [x] Verify: `GET http://localhost:8080/fhir/metadata` returns CapabilityStatement
- [x] Implement `FhirConfig` → wire `IGenericClient` bean + `FhirHttpMessageConverter`
- [x] Implement `PatientController` → `PatientService` → `PatientMapper`
- [x] Test: POST patient, GET patient by ID via Postman

---

## Phase 2 — Core Clinical (Days 3–5)

- [x] Implement `EncounterController` → `EncounterService` → `EncounterMapper`
- [x] Implement `VitalsController` → `VitalsService` → `ObservationMapper`
  - Support all 4 vitals: BP systolic, BP diastolic, weight, SpO2
  - Use correct LOINC codes (see `03-fhir-mapping.md`)
- [x] Link vitals to an encounter via `Observation.encounter` reference
- [ ] Test: Create encounter for patient, record vitals, verify in HAPI directly

---

## Phase 3 — Summary & Polish (Days 6–7)

- [x] Implement `SummaryService` (3-call FHIR aggregation → returns `Bundle`)
- [x] Add `GlobalExceptionHandler` (404, FHIR errors, validation errors)
- [x] Add patient search endpoint (`?name=&dob=`)
- [x] Add `application.yaml` with configurable `fhir.server.url`
- [x] Write `README.md` quick-start instructions
- [x] Load synthetic seed data via Synthea pipeline
- [x] Add `seed-demo-data` Nx app — Cucumber-driven, idempotent, relative-date seed data

---

## Phase 4 — Frontend (care-platform)

- [x] Bootstrap Angular Material M3 shell + theme (clinician-app)
- [x] Patient roster — searchable `mat-table` with live data from `/api/patients`
- [x] Patient detail page — 3-panel layout with ApexCharts vitals timeline
- [x] Top navigation header replacing sidebar
- [ ] Wire `/api/dashboard`, `/snapshot`, `/trends`, `/timeline` to clinician-app
- [ ] Encounter list in patient detail
- [ ] CORS configuration (needed for any non-proxied deployment)
- [x] Auth / JWT — login + refresh, roles → permissions, `@PreAuthorize` on every endpoint, clinician-app login (see [10-auth.md](./10-auth.md))

---

## Phase 5 — Clinical Dashboard Backend

- [x] 8 new FHIR CRUD resource types: Practitioner, Condition, MedicationStatement, Appointment, ServiceRequest, DiagnosticReport, CarePlan, Goal
- [x] `EncounterRequest` extended with optional `practitionerId` → `Encounter.participant[ATND]`
- [x] `ServiceRequestService.complete()` — auto-marks ServiceRequest `completed` when DiagnosticReport is created against it
- [x] `ClinicalThresholds` config (`@ConfigurationProperties`) — vital alert levels from `application.yaml`
- [x] `SnapshotService` → `GET /api/patients/{id}/snapshot` — active conditions, meds, latest vitals per LOINC, alerts
- [x] `TrendsService` → `GET /api/patients/{id}/trends?type=bp,spo2&period=30d` — paired BP series, downsampling, reference ranges
- [x] `TimelineService` → `GET /api/patients/{id}/timeline` — unified event feed, cursor pagination, practitioner name resolution
- [x] `ConditionEvaluationService` — auto-detects hypertension (3× systolic >140) and hypoxemia (2× SpO2 <95%) after vitals recording
- [x] `DashboardService` → `GET /api/dashboard/{patientId}` — partial-failure-safe aggregation with `_warnings[]`, goal progress evaluation
- [x] `GlobalExceptionHandler` extended: 422 (`ReferenceValidationException`), 503 (`FhirClientConnectionException`)
- [x] Seed data enriched — each of 5 patients has full clinical profile; each patient in its own `.feature` file

---

## Postpone (Post-MVP)

| Feature | Reason to Postpone |
|---|---|
| Patient `PUT` update | Read-then-update pattern adds complexity |
| Pagination on search | Not needed for demo data volume |
| patient-app features | Scaffolded; clinician-app is the active app |

---

## Skip Entirely (Not in Scope)

- FHIR Subscriptions / webhooks
- CDS Hooks
- SMART on FHIR
- Bulk data export (`$export`)
- HL7 v2 message handling

---

## Milestone Checklist

```
Week 1
  ✓ HAPI FHIR + Postgres running locally
  ✓ Patient create + read working
  ✓ Encounter create + read working
  ✓ Vitals create + read working (all LOINC codes correct)

Week 2
  ✓ Patient summary endpoint working
  ✓ Error handling polished
  ✓ FHIR R4 native responses (application/fhir+json)
  ✓ Seed data pipeline (Synthea → HAPI FHIR)
  ✓ README and docs complete
  ○ End-to-end Postman smoke test (pending infra start)
  ○ Push to GitHub

Frontend (care-platform)
  ✓ Angular Material M3 shell + theme (clinician-app)
  ✓ Patient roster (mat-table, live API data)
  ✓ Patient detail page (ApexCharts vitals timeline)
  ✓ seed-demo-data app (Cucumber, idempotent, 5 patient profiles — each in own .feature file)
  ○ Wire dashboard/snapshot/trends/timeline to clinician-app
  ○ Encounter list in patient detail
  ○ CORS config
  ✓ Auth / JWT (Spring Security resource server, HS256, ADMIN / CLINICIAN / FRONT_DESK)

Phase 5 — Clinical Dashboard Backend
  ✓ 11 FHIR resource types (Patient, Encounter, Observation + 8 new)
  ✓ 4 experience APIs (dashboard, snapshot, trends, timeline)
  ✓ ConditionEvaluationService (auto-detects hypertension + hypoxemia)
  ✓ ServiceRequest → DiagnosticReport lifecycle
  ✓ Practitioner context in Encounters, Appointments, ServiceRequests
  ✓ ClinicalThresholds config with alert computation
  ✓ Partial-failure-safe DashboardService
  ✓ Enriched seed data (Conditions, Meds, Appointments, Labs, CarePlans, Goals)
```
