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

| Permission | Endpoints | FRONT_DESK | CLINICIAN | ADMIN |
|---|---|:-:|:-:|:-:|
| `patient:read` | `GET /api/patients`, `GET /api/patients/{id}` | ✓ | ✓ | ✓ |
| `patient:write` | `POST /api/patients` | ✓ | ✓ | ✓ |
| `appointment:read` / `appointment:write` | `GET` / `POST /api/appointments` | ✓ | ✓ | ✓ |
| `practitioner:read` | `GET /api/practitioners/{id}` | ✓ | ✓ | ✓ |
| `practitioner:write` | `POST /api/practitioners` | | | ✓ |
| `clinical:read` | every other `GET`: encounters, vitals, conditions, medications, service-requests, diagnostic-reports, care-plans, goals, snapshot, trends, timeline, dashboard | | ✓ | ✓ |
| `encounter:write`, `vitals:write`, `condition:write`, `medication:write` | matching `POST` | | ✓ | ✓ |
| `order:write` | `POST /api/service-requests`, `POST /api/diagnostic-reports` | | ✓ | ✓ |
| `careplan:write` | `POST /api/care-plans`, `POST /api/goals` | | ✓ | ✓ |
| `alert:read` / `alert:write` | `GET /api/alerts` / `POST /api/alerts/{id}/acknowledge` | | ✓ | ✓ |

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
  "email": "john.doe@email.com"
}
```

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
  "active": null
}
```

### GET /api/patients?name=John

**Response `200 OK` — `PageResponse<PatientResponse>`:**
```json
{
  "items": [
    { "id": "123", "firstName": "John", "lastName": "Doe", "dateOfBirth": "1990-05-15", "gender": "male", "phone": "9876543210", "email": "john.doe@email.com", "active": null }
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

### POST /api/service-requests
```json
{ "patientId": "123", "encounterId": "456", "practitionerId": "pract-01", "code": "Basic Metabolic Panel", "category": "laboratory", "status": "active", "priority": "routine", "authoredOn": "2026-04-14" }
```

**Response `201 Created` — `ServiceRequestResponse`:** the same fields plus `id`. The list returns `PageResponse<ServiceRequestResponse>`.

**Note:** When a DiagnosticReport is created with `serviceRequestId`, the linked ServiceRequest is automatically marked `completed`.

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
  "pendingServiceRequests": [{ "id": "sr-01", "code": "Lipid Panel", "category": "laboratory", "orderedOn": "2026-04-28", "priority": "routine", "status": "active" }],
  "activeCarePlan": { "id": "cp-01", "title": "Hypertension Management", "status": "active", "goals": [{ "id": "goal-01", "description": "Reduce systolic BP below 130", "progress": { "currentValue": 138, "targetValue": 130, "onTrack": true, "percentToGoal": 94 } }] },
  "recentDiagnosticReport": { "id": "rpt-01", "title": "Basic Metabolic Panel", "issued": "2026-04-16", "conclusion": "Creatinine 1.1 mg/dL.", "status": "final", "serviceRequestId": "sr-789" },
  "_warnings": []
}
```

---

### GET /api/patients/{id}/snapshot

At-a-glance clinical state. Lighter than `/dashboard` — use for panel refreshes.

**Response `200 OK`:** active conditions, current medications, latest vital per type, computed alerts.

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
