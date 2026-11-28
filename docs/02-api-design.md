# API Design

Base URL: `http://localhost:9090`

All responses are plain JSON with `Content-Type: application/json`. FHIR R4 is how clinic-api stores data in HAPI; it is not the public contract.

- **Single resource** (`POST`, `GET /{id}`) → a flat response record with `id` plus the matching `*Request` fields, and a few read-only extras (`PatientResponse.active`). References are flattened to ids (`patientId`, `encounterId`, `practitionerId`, …). Fields the stored resource lacks are `null`.
- **Lists** (`GET ?patientId=`, `GET /api/patients`) → `PageResponse` `{ "items": [...], "total": n }`. `items` is only the first page of HAPI's search results (HAPI's default page size) and `total` is HAPI's total match count, so `total` can exceed `items.length`. There is no paging yet: no `limit`/`offset` parameters and no next link.
- **Dates** are ISO strings taken from the stored FHIR value, independent of the server's time zone: `yyyy-MM-dd` for dates, ISO-8601 with the stored offset for appointment times. `ObservationResponse.effectiveDateTime` is the stored string as-is (see Vitals).

---

## Authentication & roles

Every endpoint needs `Authorization: Bearer <accessToken>` except `POST /api/auth/login`, `POST /api/auth/refresh`, the alert webhook (which has its own `X-Alert-Secret`) and `/actuator/health`, `/actuator/info`, `/actuator/prometheus`. See [10-auth.md](./10-auth.md) for how it works.

### POST /api/auth/login
```json
{ "username": "clinician", "password": "Clinician#2026" }
```
**Response `200 OK`**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "username": "clinician",
    "name": "Anika Patel",
    "title": "Internal Medicine",
    "practitionerId": "user-clinician",
    "roles": ["CLINICIAN"]
  }
}
```
Wrong username or password → `401 { "status": 401, "message": "Invalid username or password" }` (the same message either way, so callers can't probe which usernames exist).

### POST /api/auth/refresh
```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }
```
Returns a new token pair in the same shape as login. An expired refresh token, or an access token sent here, → `401`.

### Tokens
| | Access token | Refresh token |
|---|---|---|
| Lifetime | 15 min (`clinic.auth.access-token-ttl`) | 7 days (`clinic.auth.refresh-token-ttl`) |
| `token_type` claim | `access` | `refresh` |
| Accepted by | every protected endpoint | only `/api/auth/refresh` |
| Other claims | `sub`, `iss`, `iat`, `exp`, `roles`, `name`, `practitioner_id` | `sub`, `iss`, `iat`, `exp` |

Both are HS256 JWTs signed with `clinic.auth.jwt-secret` (env `CLINIC_JWT_SECRET`, at least 32 bytes).

### Permissions
Tokens carry **roles**; on each request the server expands them into **permissions** (`RolePermissions`), and each endpoint checks one permission with `@PreAuthorize("hasAuthority('…')")`.

| Permission | Endpoints | FRONT_DESK | CLINICIAN | ADMIN | PATIENT |
|---|---|:-:|:-:|:-:|:-:|
| `patient:read` | `GET /api/patients` | ✓ | ✓ | ✓ | |
| `patient:read` | `GET /api/patients/{id}` | ✓ | ✓ | ✓ | own |
| `patient:write` | `POST /api/patients` | ✓ | ✓ | ✓ | |
| `appointment:read` | `GET /api/appointments?patientId=` | ✓ | ✓ | ✓ | own |
| `appointment:write` | `POST /api/appointments` | ✓ | ✓ | ✓ | |
| `practitioner:read` | `GET /api/practitioners/{id}` | ✓ | ✓ | ✓ | |
| `practitioner:write` | `POST /api/practitioners` | | | ✓ | |
| `clinical:read` | snapshot, trends, timeline, dashboard, `GET /api/vitals` | | ✓ | ✓ | own |
| `clinical:read` | every other clinical `GET`: encounters, conditions, medications, service-requests (and their `documentation`), diagnostic-reports, care-plans, goals | | ✓ | ✓ | |
| `vitals:write` | `POST /api/vitals` | | ✓ | ✓ | own |
| `encounter:write`, `condition:write`, `medication:write` | matching `POST` | | ✓ | ✓ | |
| `order:write` | `POST /api/service-requests`, `PUT /api/service-requests/{id}/documentation`, `POST /api/service-requests/{id}/documentation/complete`, `POST /api/diagnostic-reports` | | ✓ | ✓ | |
| `careplan:write` | `POST /api/care-plans`, `POST /api/goals` | | ✓ | ✓ | |
| `alert:read` / `alert:write` | `GET /api/alerts` / `POST /api/alerts/{id}/acknowledge` | | ✓ | ✓ | |
| `coverage:read` | `GET /api/coverages/{id}` | ✓ | ✓ | ✓ | |
| `coverage:read` | `GET /api/coverages?patientId=` | ✓ | ✓ | ✓ | own |
| `coverage:write` | `POST /api/coverages` | ✓ | ✓ | ✓ | |
| `coverage:write` | `PUT /api/coverages/{id}` | ✓ | ✓ | ✓ | |

The front desk records insurance at registration and check-in, and clinicians need it when they order (CRD and prior auth later), so both get `coverage:read` and `coverage:write`.

**own**: a `PATIENT` has no permissions of its own. These endpoints add `or @patientAccess.isSelf(#id)`, which is true only when the token's `patient_id` claim matches the patient in the request. Tony can read his own snapshot, but gets `403` for Priya's and for the patient list

Request-body validation (`@Valid`) runs before the permission check, so an invalid body returns `400` even for a role that would get `403`.

---

## Patient APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/patients` | Register a new patient |
| `GET` | `/api/patients/{id}` | Get patient by ID |
| `GET` | `/api/patients?name=John&dob=1990-01-01` | Search patients |

### POST /api/patients

**Request:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "dateOfBirth": "1990-05-15",
  "gender": "male",
  "phone": "9876543210",
  "email": "john.doe@email.com",
  "mrn": "PX-00012345"
}
```

`mrn` is optional. Without it, clinic-api generates one (`PX-` + 8 random digits) and stores it as `Patient.identifier` with system `http://praxis.clinic/fhir/identifier/mrn` and type `MR`.

**Response `201 Created` — `PatientResponse`:**
```json
{
  "id": "123",
  "firstName": "John",
  "lastName": "Doe",
  "dateOfBirth": "1990-05-15",
  "gender": "male",
  "phone": "9876543210",
  "email": "john.doe@email.com",
  "active": null,
  "mrn": "PX-00012345"
}
```

### GET /api/patients?name=John

**Response `200 OK` — `PageResponse<PatientResponse>`:**
```json
{
  "items": [
    { "id": "123", "firstName": "John", "lastName": "Doe", "dateOfBirth": "1990-05-15", "gender": "male", "phone": "9876543210", "email": "john.doe@email.com", "active": null, "mrn": "PX-00012345" }
  ],
  "total": 1
}
```

---

## Encounter APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/encounters` | Record a new visit |
| `GET` | `/api/encounters/{id}` | Get encounter by ID |
| `GET` | `/api/encounters?patientId={id}` | List encounters for a patient |

### POST /api/encounters

**Request:**
```json
{
  "patientId": "123",
  "visitDate": "2025-04-25",
  "reason": "Routine checkup",
  "status": "finished",
  "practitionerId": "pract-01",
  "note": "Advised low-salt diet"
}
```

**Response `201 Created` — `EncounterResponse`:**
```json
{
  "id": "456",
  "patientId": "123",
  "visitDate": "2025-04-25",
  "reason": "Routine checkup",
  "status": "finished",
  "practitionerId": "pract-01",
  "note": "Advised low-salt diet"
}
```

### GET /api/encounters?patientId=123

**Response `200 OK` — `PageResponse<EncounterResponse>`:**
```json
{ "items": [ { "id": "456", "patientId": "123", "visitDate": "2025-04-25", ... } ], "total": 1 }
```

---

## Vitals APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/vitals` | Record one or more vitals |
| `GET` | `/api/vitals?patientId={id}` | Get all vitals for a patient |
| `GET` | `/api/vitals?patientId={id}&type=weight` | Filter by vital type (`systolic`, `diastolic`, `weight`, `spo2`) |

### POST /api/vitals

Send only the vitals you have — null fields are skipped. Each non-null vital is stored as a separate FHIR `Observation`.

**Request:**
```json
{
  "patientId": "123",
  "encounterId": "456",
  "effectiveDate": "2025-04-25",
  "systolicBp": 120,
  "weightKg": 72.5
}
```

**Response `201 Created` — `ObservationResponse[]`, one per recorded vital:**
```json
[
  { "id": "789", "patientId": "123", "encounterId": "456", "type": "systolic", "loincCode": "8480-6", "value": 120.0, "unit": "mm[Hg]", "effectiveDateTime": "2025-04-25T00:00:00-04:00" },
  { "id": "790", "patientId": "123", "encounterId": "456", "type": "weight", "loincCode": "29463-7", "value": 72.5, "unit": "kg", "effectiveDateTime": "2025-04-25T00:00:00-04:00" }
]
```

`type` is one of `systolic`, `diastolic`, `weight`, `spo2`, `heartRate`, `temperature`, or `null` for observations with another LOINC code (e.g. imported Synthea data).

`effectiveDateTime` is the ISO-8601 string exactly as stored, taken from `effectiveDateTime`, `effectivePeriod.start` or `effectiveInstant` (in that order). Its precision follows the source: a date (`2025-04-25`), a date-time with offset, or an instant with milliseconds. It is `null` when the observation has none of these.

### GET /api/vitals?patientId=123

**Response `200 OK` — `PageResponse<ObservationResponse>`.**

---

## Practitioner APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/practitioners` | Register a practitioner |
| `GET` | `/api/practitioners/{id}` | Get practitioner by ID |

### POST /api/practitioners
```json
{ "firstName": "Aisha", "lastName": "Patel", "specialty": "Internal Medicine", "npi": "1234567890", "email": "a.patel@clinic.com" }
```

**Response `201 Created` — `PractitionerResponse`:** the same fields plus `id`.

---

## Condition APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/conditions` | Record a diagnosis |
| `GET` | `/api/conditions/{id}` | Get condition |
| `GET` | `/api/conditions?patientId={id}` | List conditions for patient |

### POST /api/conditions
```json
{ "patientId": "123", "encounterId": "456", "code": "59621000", "display": "Essential hypertension", "clinicalStatus": "active", "onsetDate": "2024-01-15" }
```

**Response `201 Created` — `ConditionResponse`:** the same fields plus `id`. The list returns `PageResponse<ConditionResponse>`.

---

## Medication APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/medications` | Record a medication |
| `GET` | `/api/medications?patientId={id}` | List medications for patient |

### POST /api/medications
```json
{ "patientId": "123", "medicationName": "Lisinopril 10mg", "status": "active", "dosageText": "Once daily", "startDate": "2024-01-20" }
```

**Response `201 Created` — `MedicationResponse`:** the same fields plus `id`. The list returns `PageResponse<MedicationResponse>`.

---

## Appointment APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/appointments` | Schedule an appointment |
| `GET` | `/api/appointments?patientId={id}` | List appointments for patient |

### POST /api/appointments
```json
{ "patientId": "123", "practitionerId": "pract-01", "start": "2026-06-01T09:00:00", "end": "2026-06-01T09:30:00", "description": "3-month hypertension review", "status": "booked" }
```

**Response `201 Created` — `AppointmentResponse`:**
```json
{ "id": "appt-01", "patientId": "123", "practitionerId": "pract-01", "start": "2026-06-01T09:00:00-04:00", "end": "2026-06-01T09:30:00-04:00", "description": "3-month hypertension review", "status": "booked" }
```

`start` and `end` carry the server's UTC offset. The list returns `PageResponse<AppointmentResponse>`.

---

## ServiceRequest APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/service-requests` | Place a lab or imaging order |
| `GET` | `/api/service-requests/{id}` | Get order |
| `GET` | `/api/service-requests?patientId={id}&status=active` | List orders (optional status filter) |
| `GET` | `/api/service-requests/{id}/documentation` | The order's DTR form: saved answers, or answers prefilled from the chart |
| `PUT` | `/api/service-requests/{id}/documentation` | Save answers as an `in-progress` QuestionnaireResponse |
| `POST` | `/api/service-requests/{id}/documentation/complete` | Check required items and save the response as `completed` |

### POST /api/service-requests
```json
{ "patientId": "123", "encounterId": "456", "practitionerId": "pract-01", "display": "Basic Metabolic Panel", "codeSystem": "http://loinc.org", "code": "51990-0", "category": "laboratory", "status": "active", "priority": "routine", "authoredOn": "2026-04-14" }
```

`display` is the order's text and is required. `codeSystem` and `code` are an optional coding (LOINC, SNOMED, HCPCS or CPT); give both or neither, otherwise the request gets `400`.

**Response `201 Created` — `ServiceRequestResponse`:** the same fields plus `id`. The list returns `PageResponse<ServiceRequestResponse>`.

`ServiceRequestResponse` also has `coverage` and `cards[]`, the payer's CRD answer. Before the order is saved, clinic-api calls the payer's `order-sign` hook (`POST /cds-services/order-sign-crd` on payer-api, with a `system/Coverage.r` token) with the draft order, the patient and their active Coverage. The returned `coverage-information` is stored on the ServiceRequest and the cards are stored with it, so `GET` returns them too.

```json
{ "id": "sr-02", "patientId": "sandra-okafor", "display": "MRI lumbar spine without contrast", "codeSystem": "http://www.ama-assn.org/go/cpt", "code": "72148", "category": "imaging", "status": "active", "priority": "routine", "authoredOn": "2026-11-11",
  "coverage": { "coverageId": "coverage-sandra-okafor", "covered": "covered", "paNeeded": "auth-needed", "docNeeded": "clinical", "reason": "Clinical documentation is needed for the prior authorization request", "questionnaire": "http://praxishealthplan.example/fhir/Questionnaire/mri-lumbar-spine", "date": "2026-11-11", "coverageAssertionId": "65f84693-1eca-398b-b3da-1bec7a3a0125" },
  "cards": [{ "summary": "Prior authorization needed: MRI lumbar spine without contrast", "indicator": "warning", "detail": "Praxis Health Plan needs prior authorization for this order. Complete the documentation questionnaire, then submit the prior authorization request.", "source": "Praxis Health Plan" }] }
```

- `coverage` is `null` when the patient has no active Coverage with this payer (`clinic.payer.payer-id`, matched on the Coverage's payor identifier; no hook call and no patient data sent, `cards` is `[]`) or when the payer could not be reached.
- The call fails open (NFR-REL-2): after a 2 s timeout, a 5xx or a connection error the order is still saved, with `coverage: null` and one `"Coverage unknown"` warning card from `clinic-api`.
- `POST /api/appointments` calls `appointment-book` the same way and stores the returned `coverage-information` on the Appointment; `AppointmentResponse` is unchanged.

### DTR documentation: /api/service-requests/{id}/documentation

The form comes from the payer: clinic-api calls payer-api `POST /fhir/Questionnaire/$questionnaire-package` (system token with `system/Questionnaire.r`) with the order and the patient's active Coverage with this payer, and payer-api picks the Questionnaire for the order code ([§6](11-payer-architecture.md#6-coverage-rules)). The answers are stored in the provider HAPI as one `QuestionnaireResponse` per order (see [03-fhir-mapping.md](03-fhir-mapping.md#questionnaireresponse-dtr-documentation)).

**`GET` → `DocumentationResponse`:** the saved response for the order, or, before the first save, a new one prefilled from the chart with the Questionnaire's FHIRPath expressions (nothing is stored; `questionnaireResponseId` is `null`).

```json
{ "serviceRequestId": "sr-02", "questionnaireResponseId": null,
  "questionnaire": "http://praxishealthplan.example/fhir/Questionnaire/mri-lumbar-spine",
  "title": "MRI lumbar spine documentation", "status": "in-progress",
  "items": [
    { "linkId": "neurological-findings", "text": "Neurological findings", "type": "group", "required": false, "repeats": false, "readOnly": false,
      "options": [], "enableWhen": [], "enableBehavior": null, "answers": [],
      "items": [
        { "linkId": "neurological-deficit", "text": "Does the patient have a progressive or severe neurological deficit?", "type": "boolean", "required": true, "repeats": false, "readOnly": false,
          "options": [], "enableWhen": [], "enableBehavior": null,
          "answers": [{ "value": { "valueBoolean": false, "valueString": null, "valueInteger": null, "valueDecimal": null, "valueDate": null, "valueDateTime": null, "valueCoding": null }, "prefilled": true }], "items": [] },
        { "linkId": "neurological-deficit-description", "text": "Describe the deficit …", "type": "text", "required": true, "repeats": false, "readOnly": false,
          "options": [], "enableWhen": [{ "question": "neurological-deficit", "operator": "=", "answer": { "valueBoolean": true, … } }], "enableBehavior": null, "answers": [], "items": [] } ] } ] }
```

- `items` follow the Questionnaire, `display` and `group` items included. `type` is the FHIR item type (`group`, `display`, `string`, `text`, `boolean`, `integer`, `decimal`, `date`, `dateTime`, `choice`, `open-choice`).
- A value has exactly one of `valueString`, `valueInteger`, `valueDecimal`, `valueBoolean`, `valueDate`, `valueDateTime`, `valueCoding` (`{system, code, display}`); the others are `null`.
- `options` are the item's `answerOption`s, or the codes of its `answerValueSet` from the package.
- `prefilled` is `true` when the answer came from the chart (`information-origin` `auto-client`) and has not been changed.

**`PUT` with `DocumentationRequest` → `DocumentationResponse`:**

```json
{ "answers": [ { "linkId": "pain-location", "values": [{ "valueString": "L5, left side" }] },
               { "linkId": "clinical-notes", "values": [] } ] }
```

- Each listed `linkId` gets exactly these values; an empty `values` clears it. Items not listed keep their saved (or, before the first save, prefilled) answers.
- The first save creates the response; later saves update the same one.
- Answers of items whose `enableWhen` is not met are dropped.
- An unchanged prefilled answer stays `auto-client`; a changed one is `override` and a new one `manual`, both with the signed-in clinician (`practitioner_id` claim) as author. A login without `practitioner_id` cannot save (`422`).

**`POST .../complete`** takes the same body (optional) and saves the response as `completed` once every enabled `required` question has an answer.

| Status | When |
|---|---|
| `400` | `answers` or a `linkId` is missing |
| `404` | unknown order, the patient has no active Coverage with this payer (nothing is sent to the payer), or the payer needs no documentation for the order |
| `409` | the documentation is already `completed` |
| `422` | an unknown `linkId`, a value that does not fit the item type or its options, several values for a non-repeating item, missing required answers on `complete` (`"Required questions are not answered: …"`), or a login without a `practitioner_id` (`PUT` and `complete`) |
| `503` | payer-api could not be reached, timed out (2 s) or answered with an error; the form cannot be built without the Questionnaire, so there is no fallback |

**Note:** When a DiagnosticReport is created with `serviceRequestId`, the linked ServiceRequest is automatically marked `completed`. If that ServiceRequest belongs to a different patient, the request fails with `422` and nothing is stored.

---

## Prior authorization APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/prior-auths` | Submit a saved order to its payer and return its current status (`priorauth:write`) |
| `GET` | `/api/prior-auths?patientId={id}` | List the chart's tracked authorizations as an array (`priorauth:read`) |

Both permissions are granted to clinicians and administrators. Submission requires the order ID, the two-digit place of service and the X12 service category. Supply a CPT or HCPCS `service` coding when the order has no suitable billing code. `diagnoses` can supply ICD-10-CM codings.

```json
{ "orderId": "sr-02", "placeOfService": "11", "serviceCategory": "1" }
```

The POST returns HTTP 200 with the tracked result. A repeated POST for the same tracked order returns the current result.

```json
{ "orderId": "sr-02", "status": "APPROVED", "authorizationNumber": "PA-1234",
  "denialReasonCode": null, "denialReason": null }
```

The status values are `SUBMITTED`, `PENDED`, `APPROVED`, `DENIED`, `NOT_REQUIRED`, and `MIXED`. The authorization number and denial fields are nullable. An unknown order returns 404. Invalid billing or a missing submission prerequisite returns 400. An unconfirmed payer response returns 503.

---

## Coverage APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/coverages` | Record a patient's insurance |
| `GET` | `/api/coverages/{id}` | Get coverage |
| `PUT` | `/api/coverages/{id}` | Edit coverage |
| `GET` | `/api/coverages?patientId={id}` | List a patient's coverages |

### POST /api/coverages
```json
{ "patientId": "123", "payerId": "praxis-health-plan", "payerName": "Praxis Health Plan", "memberId": "PHP-10001", "planName": "Praxis Gold PPO", "status": "active", "periodStart": "2026-01-01", "periodEnd": null, "relationship": "self" }
```

`planName`, `periodEnd` and `relationship` (default `self`) are optional. `status` is `active`, `cancelled`, `draft` or `entered-in-error`.

`memberId` is the payer's member id (`PHP-#####`), stored under the payer's identifier system `http://praxishealthplan.example/fhir/identifier/member-id`, so the provider and payer `Coverage` share one identifier.

The payer is a FHIR `Organization` found by its identifier (`http://praxis.clinic/fhir/identifier/payer|{payerId}`). If none exists, clinic-api creates it, so every coverage of one payer points at the same `Organization`.

**Response `201 Created` — `CoverageResponse`:** the same fields plus `id` and `payerOrganizationId`. The list returns `PageResponse<CoverageResponse>`.

### PUT /api/coverages/{id}
Takes the same body as `POST` and replaces the stored coverage (payer found or created the same way). `patientId` must match the coverage's beneficiary, else `422`. Unknown id: `404`. **Response `200 OK` — `CoverageResponse`.**

---

## DiagnosticReport APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/diagnostic-reports` | Create a lab/imaging report |
| `GET` | `/api/diagnostic-reports?patientId={id}` | List reports for patient |

### POST /api/diagnostic-reports
```json
{ "patientId": "123", "encounterId": "456", "serviceRequestId": "sr-789", "title": "Basic Metabolic Panel", "status": "final", "issued": "2026-04-16", "conclusion": "Electrolytes normal. Creatinine 1.1 mg/dL.", "resultIds": ["obs-01"] }
```

**Response `201 Created` — `DiagnosticReportResponse`:** the same fields plus `id`; `resultIds` is `[]` when there are no results. The list returns `PageResponse<DiagnosticReportResponse>`.

---

## CarePlan APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/care-plans` | Create a care plan |
| `GET` | `/api/care-plans?patientId={id}` | List care plans for patient |

### POST /api/care-plans
```json
{ "patientId": "123", "conditionIds": ["cond-01"], "title": "Hypertension Management", "status": "active", "periodStart": "2026-01-01", "goalIds": ["goal-01"] }
```

**Response `201 Created` — `CarePlanResponse`:** the same fields plus `id`; `conditionIds` and `goalIds` are `[]` when empty. The list returns `PageResponse<CarePlanResponse>`.

---

## Goal APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/goals` | Create a clinical goal |
| `GET` | `/api/goals?patientId={id}` | List goals for patient |

### POST /api/goals
```json
{ "patientId": "123", "description": "Reduce systolic BP below 130", "status": "active", "targetMeasureCode": "8480-6", "targetMeasureDisplay": "Systolic blood pressure", "targetValue": 130.0, "targetUnit": "mm[Hg]", "targetDate": "2026-07-01" }
```

**Response `201 Created` — `GoalResponse`:** the same fields plus `id`. The target fields are `null` for goals without a measurable target. The list returns `PageResponse<GoalResponse>`.

---

## Experience APIs (plain JSON — not FHIR)

### GET /api/dashboard/{patientId}

Single aggregated response for the 3-panel clinical dashboard. Partial-failure safe — each section degrades independently; failed sections return `null` with a message in `_warnings[]`.

**Response `200 OK`:**
```json
{
  "patient": { "id": "123", "name": "Marcus Webb", "dob": "1965-03-12", "gender": "male" },
  "careTeam": {
    "primaryDoctor": { "id": "pract-01", "name": "Dr. Aisha Patel", "specialty": "Internal Medicine" },
    "organization": { "id": "seed-default-org", "name": "Seed Dev Clinic" }
  },
  "snapshot": {
    "activeConditions": [{ "id": "cond-01", "display": "Essential hypertension", "onsetDate": "2024-01-15", "status": "active" }],
    "currentMedications": [{ "id": "med-01", "name": "Lisinopril 10mg", "dosage": "Once daily", "status": "active" }],
    "latestVitals": { "systolicBp": { "value": 138, "unit": "mmHg", "date": "2026-04-28", "flagged": false } },
    "alerts": [{ "severity": "warning", "type": "vital", "message": "Systolic BP 162 mmHg above 140 threshold", "resourceId": null }]
  },
  "upcomingAppointment": { "id": "appt-01", "start": "2026-06-01", "description": "3-month hypertension review", "status": "booked", "provider": "Dr. Aisha Patel" },
  "recentEncounters": [{ "id": "enc-01", "date": "2026-04-28", "reason": "BP check", "status": "finished" }],
  "pendingServiceRequests": [{ "id": "sr-01", "code": "Lipid Panel", "category": "laboratory", "orderedOn": "2026-04-28", "priority": "routine", "status": "active", "coverage": null, "cards": [] }],
  "activeCarePlan": { "id": "cp-01", "title": "Hypertension Management", "status": "active", "goals": [{ "id": "goal-01", "description": "Reduce systolic BP below 130", "progress": { "currentValue": 138, "targetValue": 130, "onTrack": true, "percentToGoal": 94 } }] },
  "recentDiagnosticReport": { "id": "rpt-01", "title": "Basic Metabolic Panel", "issued": "2026-04-16", "conclusion": "Creatinine 1.1 mg/dL.", "status": "final", "serviceRequestId": "sr-789" },
  "_warnings": []
}
```

Each pending order also has the `coverage` and `cards[]` stored with it, the same as `ServiceRequestResponse`, so the order list can show its CRD badge.

---

### GET /api/patients/{id}/snapshot

At-a-glance clinical state. Lighter than `/dashboard` — use for panel refreshes.

**Response `200 OK`:** active conditions, current medications, latest vital per type, computed alerts, and `coverage`: the patient's most recently updated `active` coverage as a `CoverageResponse`, or `null` when there is none.

---

### GET /api/patients/{id}/trends?type=bp,spo2,weight&period=30d

Chart-ready vitals series. `type=bp` returns paired `systolic[]` + `diastolic[]`. Periods: `7d`, `30d`, `90d`, `1y`.

**Response `200 OK`:**
```json
{
  "period": "30d", "from": "2026-04-02", "to": "2026-05-02",
  "series": {
    "bp": { "systolic": [{ "date": "2026-04-14", "value": 138 }], "diastolic": [{ "date": "2026-04-14", "value": 88 }], "referenceRange": { "systolicMax": 140, "diastolicMax": 90 } },
    "weight": { "values": [{ "date": "2026-04-14", "value": 89.5 }], "unit": "kg" }
  }
}
```

---

### GET /api/patients/{id}/timeline?limit=20&before=2026-05-01&types=encounter,report

Unified chronological event feed. Supports cursor pagination (`before` = ISO date, exclusive). Each event has a common shape regardless of FHIR resource type.

**Response `200 OK` — `TimelineEvent[]`:**
```json
[
  { "id": "enc-01", "type": "encounter", "date": "2026-04-28", "title": "Clinic Visit", "subtitle": "BP check — improved control", "status": "finished", "resourceId": "Encounter/enc-01", "metadata": { "provider": "Dr. Aisha Patel" } },
  { "id": "sr-01", "type": "service-request", "date": "2026-04-14", "title": "Lab Test Ordered", "subtitle": "Basic Metabolic Panel", "status": "completed", "resourceId": "ServiceRequest/sr-01", "metadata": { "category": "laboratory", "orderedBy": "Dr. Aisha Patel" } },
  { "id": "rpt-01", "type": "report", "date": "2026-04-16", "title": "Lab Results Available", "subtitle": "Basic Metabolic Panel", "status": "final", "resourceId": "DiagnosticReport/rpt-01", "metadata": { "serviceRequestId": "sr-01" } },
  { "id": "appt-01", "type": "appointment", "date": "2026-06-01", "title": "Upcoming Appointment", "subtitle": "3-month hypertension review", "status": "booked", "resourceId": "Appointment/appt-01", "metadata": { "provider": "Dr. Aisha Patel" } }
]
```

---

## Alert APIs

### POST /api/alerts/bp-subscription

Webhook called by HAPI FHIR's rest-hook Subscription `Subscription/clinic-high-systolic-bp` (registered by clinic-api at startup) whenever a systolic BP Observation (LOINC `8480-6`) above `clinical.thresholds.systolic-critical` is stored. The notification has an empty body, so no PHI crosses the wire. It must carry the `X-Alert-Secret` header matching `clinic.alerts.webhook-secret`, otherwise clinic-api returns `401`. clinic-api then searches for the new critical readings and sends one alert per reading through the `Notifier` (default: mock SMS written to the log) with only resource references. Two notifiers run: `LoggingSmsNotifier` writes a mock SMS to the log, and `FlagNotifier` stores the alert as a FHIR `Flag` so it shows up in the clinician-app Inbox:

```
[mock-sms] Clinician alert: Systolic BP above 180 mmHg for Patient/54 (Observation/55)
```

**Response `204 No Content`**, or `401` with `{ "status": 401, "message": "Missing or invalid webhook secret" }`. See [09-bp-alerts-demo.md](./09-bp-alerts-demo.md) for a scripted demo.

### GET /api/alerts?status=active
Requires `alert:read`. Lists stored high-BP alerts, newest first. `status` is optional (`active` or `inactive`); any other value → `400`.

**Response `200 OK`**: `PageResponse<AlertResponse>`
```json
{
  "items": [
    {
      "id": "301",
      "patientId": "54",
      "observationId": "55",
      "reason": "Systolic BP above 180 mmHg",
      "raisedAt": "2026-10-22T16:41:07Z",
      "status": "active"
    }
  ],
  "total": 1
}
```

Each alert is a FHIR `Flag`:
- `subject`: the Patient
- the `flag-detail` extension: the Observation
- `code.text`: the threshold reason
- `period.start`: when it was raised
- an identifier keyed by the Observation id
- a `meta.tag` (`http://praxis.clinic/fhir/CodeSystem/alert-status|active` or `inactive`)

R4 `Flag` can't be searched by `code` or `status`, so the list endpoint searches by the identifier system and the status tag. HAPI keeps old tags when a resource is updated, so acknowledging also removes the `active` tag with `$meta-delete`.

It holds **references only**: no names and no reading values. A reading is stored at most once (`FlagNotifier` checks the identifier first), so re-processing the lookback window after a restart never duplicates or re-opens an alert.

### POST /api/alerts/{id}/acknowledge
Requires `alert:write`. Sets the Flag to `inactive` and closes `period.end`. Returns the updated `AlertResponse`. Acknowledging an already inactive alert changes nothing. An unknown id → `404`.

---

## Error Response

Errors return plain JSON (not FHIR) with `Content-Type: application/json`:
```json
{ "status": 404, "message": "Patient with id '999' not found" }
```

| Status | Cause |
|---|---|
| `400` | DTO validation failure (`@Valid`) |
| `401` | Missing, invalid or expired token; wrong login credentials; bad webhook secret |
| `403` | Valid token, but the role lacks the endpoint's permission |
| `404` | FHIR resource not found |
| `422` | Referential integrity failure (e.g. Condition references non-existent Patient) |
| `503` | HAPI FHIR server unreachable |
