# Clinic FHIR API — Integration Tests

Postman / Newman integration test collections for clinic-api.

## Structure

One collection per controller. Each collection is fully self-contained:
- **Setup**: creates a practitioner + patient + any required dependencies
- **Test**: exercises every endpoint owned by that controller
- **Teardown**: cascade-deletes patient (removes all linked FHIR resources), then deletes practitioner

Collections are independent — they share no state and can run in any order.

```
01-patient.postman_collection.json          PatientController + /snapshot /trends /timeline
02-practitioner.postman_collection.json     PractitionerController
03-encounter.postman_collection.json        EncounterController
04-vitals.postman_collection.json           VitalsController
05-condition.postman_collection.json        ConditionController
06-medication.postman_collection.json       MedicationController
07-appointment.postman_collection.json      AppointmentController
08-service-request.postman_collection.json  ServiceRequestController
09-diagnostic-report.postman_collection.json DiagnosticReportController
10-care-plan.postman_collection.json        CarePlanController
11-goal.postman_collection.json             GoalController
12-dashboard.postman_collection.json        DashboardController (full patient setup)

clinic-local.postman_environment.json       Environment — baseUrl, fhirUrl, dev login
```

## Prerequisites

1. HAPI FHIR JPA server running at `http://localhost:8080`
2. clinic-api Spring Boot server running at `http://localhost:9090`

Seed data is **not required** — every collection creates and destroys its own data.

## Running in Postman

1. Import all `*.postman_collection.json` files
2. Import `clinic-local.postman_environment.json`
3. Select **Clinic FHIR — Local** environment
4. Run any collection — requests must run in order (top to bottom)

## Running via Newman

```bash
# Run a single collection
npx newman run 05-condition.postman_collection.json \
  -e clinic-local.postman_environment.json

# Run all collections in order
for f in $(ls *.postman_collection.json | sort); do
  npx newman run "$f" -e clinic-local.postman_environment.json || { echo "Failed: $f"; exit 1; }
done
```

## Status Codes

| Operation | Expected status |
|---|---|
| POST (create FHIR resource) | 201 Created |
| GET (read / search) | 200 OK |
| DELETE (cascade) | 200 or 204 |
| Not found | 404 |
| Validation error | 400 |

## Authentication

Every collection's pre-request script sends `Authorization: Bearer {{BEARER_TOKEN}}`. When `BEARER_TOKEN` is empty it first logs in with the environment's `username` / `password` (dev `admin` account, which can also create practitioners) and stores the access token. Access tokens last 15 minutes; clear `BEARER_TOKEN` to force a fresh login. To check role restrictions by hand, switch `username` / `password` to `frontdesk` / `FrontDesk#2026` and watch the clinical requests return 403.

## Key Side Effects to Verify

- `09-diagnostic-report`: creating a DiagnosticReport that references a ServiceRequest automatically sets the SR `status` to `"completed"` (tested inline)
- `04-vitals`: each vital field (systolicBp, diastolicBp, weightKg, spo2Percent, heartRateBpm, temperatureCelsius) maps to a distinct FHIR Observation with a specific LOINC code
