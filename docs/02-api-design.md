# API Design

Base URL: `http://localhost:9090`

All responses are native FHIR R4 resources with `Content-Type: application/fhir+json`.

---

## Patient APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/patients` | Register a new patient |
| `GET` | `/api/patients/{id}` | Get patient by FHIR ID |
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

**Response `201 Created` — `Patient` resource:**
```json
{
  "resourceType": "Patient",
  "id": "123",
  "name": [{ "family": "Doe", "given": ["John"] }],
  "birthDate": "1990-05-15",
  "gender": "male",
  "telecom": [
    { "system": "phone", "value": "9876543210" },
    { "system": "email", "value": "john.doe@email.com" }
  ]
}
```

### GET /api/patients?name=John

**Response `200 OK` — `Bundle` (searchset):**
```json
{
  "resourceType": "Bundle",
  "type": "searchset",
  "entry": [
    { "resource": { "resourceType": "Patient", "id": "123", "name": [{ "family": "Doe", "given": ["John"] }], ... } }
  ]
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
  "status": "finished"
}
```

**Response `201 Created` — `Encounter` resource:**
```json
{
  "resourceType": "Encounter",
  "id": "456",
  "status": "finished",
  "subject": { "reference": "Patient/123" },
  "period": { "start": "2025-04-25T00:00:00+05:30" },
  "reasonCode": [{ "text": "Routine checkup" }]
}
```

### GET /api/encounters?patientId=123

**Response `200 OK` — `Bundle` (searchset):**
```json
{
  "resourceType": "Bundle",
  "type": "searchset",
  "entry": [
    { "resource": { "resourceType": "Encounter", "id": "456", ... } }
  ]
}
```

---

## Vitals APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/vitals` | Record one or more vitals |
| `GET` | `/api/vitals?patientId={id}` | Get all vitals for a patient |
| `GET` | `/api/vitals?patientId={id}&type=weight` | Filter by vital type (`systolic`, `diastolic`, `weight`, `spo2`) |

### POST /api/vitals

Send only the vitals you have — null fields are skipped. Each non-null vital becomes a separate `Observation`.

**Request:**
```json
{
  "patientId": "123",
  "encounterId": "456",
  "effectiveDate": "2025-04-25",
  "systolicBp": 120,
  "diastolicBp": 80,
  "weightKg": 72.5,
  "spo2Percent": 98.0
}
```

**Response `201 Created` — `Bundle` (collection) of created Observations:**
```json
{
  "resourceType": "Bundle",
  "type": "collection",
  "entry": [
    {
      "resource": {
        "resourceType": "Observation",
        "id": "789",
        "status": "final",
        "code": { "coding": [{ "system": "http://loinc.org", "code": "8480-6", "display": "Systolic blood pressure" }] },
        "subject": { "reference": "Patient/123" },
        "encounter": { "reference": "Encounter/456" },
        "valueQuantity": { "value": 120, "unit": "mm[Hg]", "system": "http://unitsofmeasure.org" }
      }
    },
    {
      "resource": {
        "resourceType": "Observation",
        "id": "790",
        "status": "final",
        "code": { "coding": [{ "system": "http://loinc.org", "code": "29463-7", "display": "Body weight" }] },
        "valueQuantity": { "value": 72.5, "unit": "kg" }
      }
    }
  ]
}
```

---

## Summary API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/patients/{id}/summary` | Full patient summary (aggregation) |

**Response `200 OK` — `Bundle` (collection) of Patient + Encounters + Observations:**
```json
{
  "resourceType": "Bundle",
  "type": "collection",
  "entry": [
    { "resource": { "resourceType": "Patient", "id": "123", ... } },
    { "resource": { "resourceType": "Encounter", "id": "456", ... } },
    { "resource": { "resourceType": "Observation", "id": "789", ... } }
  ]
}
```

---

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

**Note:** When a DiagnosticReport is created with `serviceRequestId`, the linked ServiceRequest is automatically marked `completed`.

---

## DiagnosticReport APIs

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/diagnostic-reports` | Create a lab/imaging report |
| `GET` | `/api/diagnostic-reports?patientId={id}` | List reports for patient |

### POST /api/diagnostic-reports
```json
{ "patientId": "123", "encounterId": "456", "serviceRequestId": "sr-789", "title": "Basic Metabolic Panel", "status": "final", "issued": "2026-04-16", "conclusion": "Electrolytes normal. Creatinine 1.1 mg/dL." }
```

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

Webhook called by HAPI FHIR's rest-hook Subscription `Subscription/clinic-high-systolic-bp` (registered by clinic-api at startup) whenever a systolic BP Observation (LOINC `8480-6`) above `clinical.thresholds.systolic-critical` is stored. The notification has an empty body, so no PHI crosses the wire. clinic-api then searches for the new critical readings and sends one alert per reading through the `Notifier` (default: mock SMS written to the log) with only resource references:

```
[mock-sms] Clinician alert: Systolic BP above 180 mmHg for Patient/54 (Observation/55)
```

**Response `204 No Content`.** See [09-bp-alerts-demo.md](./09-bp-alerts-demo.md) for a scripted demo.

---

## Error Response

Errors return plain JSON (not FHIR) with `Content-Type: application/json`:
```json
{ "status": 404, "message": "Patient with id '999' not found" }
```

| Status | Cause |
|---|---|
| `400` | DTO validation failure (`@Valid`) |
| `404` | FHIR resource not found |
| `422` | Referential integrity failure (e.g. Condition references non-existent Patient) |
| `503` | HAPI FHIR server unreachable |
