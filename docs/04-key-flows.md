# Key Flows

---

## Flow 1: Register Patient

```
POST /api/patients  { firstName, lastName, dateOfBirth, gender, phone }
  │
  ├─ PatientController.createPatient(@Valid dto)
  │
  ├─ PatientService.createPatient(dto)
  │     ├─ PatientMapper.toFhir(dto)            → builds FHIR Patient resource
  │     ├─ fhirClient.create()
  │     │       .resource(fhirPatient)
  │     │       .execute()                      → POST to HAPI FHIR
  │     │       returns MethodOutcome (has new ID)
  │     ├─ fhirClient.read().withId(id)         → fetch saved Patient resource
  │     └─ PatientMapper.toResponse(patient)    → flat PatientResponse
  │
  └─ 201 Created  { "id": "123", "firstName": "...", "lastName": "...", "dateOfBirth": "...", ... }
```

---

## Flow 2: Record a Visit (Encounter)

```
POST /api/encounters  { patientId: "123", visitDate, reason, status }
  │
  ├─ EncounterController → EncounterService.createEncounter(dto)
  │     ├─ EncounterMapper.toFhir(dto)
  │     │     sets subject = Reference("Patient/123")
  │     ├─ fhirClient.create().resource(encounter).execute()
  │     ├─ fhirClient.read().withId(id)         → fetch saved Encounter resource
  │     └─ EncounterMapper.toResponse(encounter) → EncounterResponse
  │
  └─ 201 Created  { "id": "456", "patientId": "123", "visitDate": "...", "status": "...", ... }
```

---

## Flow 3: Add Vitals

```
POST /api/vitals  { patientId: "123", encounterId: "456",
                    weightKg: 72.5, spo2Percent: 98.0 }
  │
  ├─ VitalsController → VitalsService.recordVitals(dto)
  │     │
  │     ├─ ObservationMapper.toFhirObservations(dto)
  │     │     → List<Observation> (one per non-null field, with LOINC codes)
  │     │
  │     ├─ for each Observation:
  │     │     fhirClient.create().resource(obs).execute()
  │     │     fhirClient.read().withId(id)       → fetch saved Observation
  │     │     ObservationMapper.toResponse(obs)  → ObservationResponse
  │     │
  │     └─ return List<ObservationResponse>
  │
  └─ 201 Created  [ { "id": "...", "type": "weight", "loincCode": "29463-7", "value": 72.5, ... }, ... ]
```

---

## Flow 4: List a Patient's Encounters

```
GET /api/encounters?patientId=123
  │
  ├─ EncounterController → EncounterService.getEncountersForPatient("123")
  │     ├─ fhirClient.search().forResource(Encounter)
  │     │       .where(subject = Patient/123)     → Bundle (searchset)
  │     └─ PageMapper.toPage(bundle, Encounter.class, EncounterMapper::toResponse)
  │
  └─ 200 OK  { "items": [ { "id": "456", "patientId": "123", ... } ], "total": 1 }
```

---

## Flow 5: Error — Patient Not Found

```
GET /api/patients/999
  │
  ├─ PatientService.getPatient("999") → findPatient("999")
  │     fhirClient.read().resource(Patient.class).withId("999").execute()
  │     HAPI throws ResourceNotFoundException (HTTP 404)
  │     caught → rethrown as domain ResourceNotFoundException
  │
  ├─ GlobalExceptionHandler.handleNotFound(ex)
  │
  └─ 404 Not Found  { "status": 404, "message": "Patient with id '999' not found" }
```
