# FHIR Mapping Strategy

All FHIR resources use **R4** (Release 4).

---

## US Core profiles

The mappers set `meta.profile` to the US Core 7.0.0 profile (the version pinned in [11-payer-architecture.md](11-payer-architecture.md#5-implementation-guides)) and fill the elements that profile requires.

| Resource | `meta.profile` | Required elements the mapper fills |
|---|---|---|
| Patient | `http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient` | `identifier` (MRN), `name`, `gender` |
| Practitioner | `…/us-core-practitioner` | `name.family`; `identifier` needs the NPI (see below) |
| Encounter | `…/us-core-encounter` | `class` = `v3-ActCode#AMB`, `type` = SNOMED `308335008` "Patient encounter procedure" |
| Condition | `…/us-core-condition-problems-health-concerns` | `category` = `condition-category#problem-list-item` |
| ServiceRequest | `…/us-core-servicerequest` | `intent` = `order`, `code` |
| Coverage | `…/us-core-coverage` | `beneficiary`, `relationship`, `payor`, member id with type `v2-0203#MB` |
| Organization (payer) | `…/us-core-organization` | `active`, `name` |

Dates without a time (`visitDate`, `onsetDate`, `authoredOn`, coverage periods) are stored at day precision (`2026-05-01`), so the stored value never depends on the server's time zone.

US Core requires at least one `Practitioner.identifier`. Staff practitioners always have the username identifier; a practitioner registered without an NPI is not US Core conformant, and the prior auth submit (E4-S1) rejects orders from one.

The golden fixtures in `fixtures/davinci/provider/` hold the expected mapper output for each profile. The mapper tests compare their output with these files and write it to `clinic-api/target/fhir-out/provider/`.

---

## Patient → FHIR Patient

| Request Field | FHIR Path |
|---|---|
| `firstName` + `lastName` | `Patient.name[0]` (`HumanName`: `family`, `given`) |
| `dateOfBirth` | `Patient.birthDate` |
| `gender` | `Patient.gender` (`AdministrativeGender` enum) |
| `phone` | `Patient.telecom[system=phone]` |
| `email` | `Patient.telecom[system=email]` |
| `mrn` *(optional, generated when missing)* | `Patient.identifier[system=http://praxis.clinic/fhir/identifier/mrn]`, `type` = `v2-0203#MR`, `use` = `usual` |

```java
public Patient toFhir(PatientRequest request) {
    Patient patient = new Patient();
    patient.addName().setFamily(request.lastName()).addGiven(request.firstName());
    patient.setBirthDateElement(new DateType(request.dateOfBirth().toString()));
    patient.setGender(Enumerations.AdministrativeGender.fromCode(request.gender().toLowerCase()));
    if (request.phone() != null)
        patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue(request.phone());
    if (request.email() != null)
        patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.EMAIL).setValue(request.email());
    return patient;
}
```

---

## Visit → FHIR Encounter

| Request Field | FHIR Path |
|---|---|
| `patientId` | `Encounter.subject` (Reference `Patient/{id}`) |
| `visitDate` | `Encounter.period.start` |
| `reason` | `Encounter.reasonCode[0].text` |
| `status` | `Encounter.status` (e.g., `"finished"`) |
| *(fixed)* | `Encounter.class` = `AMB`, `Encounter.type[0]` = SNOMED `308335008` |

---

## Order → FHIR ServiceRequest

| Request Field | FHIR Path |
|---|---|
| `patientId` | `ServiceRequest.subject` |
| `encounterId` *(optional)* | `ServiceRequest.encounter` |
| `practitionerId` *(optional)* | `ServiceRequest.requester` |
| `display` | `ServiceRequest.code.text` |
| `codeSystem` + `code` *(optional, both or neither)* | `ServiceRequest.code.coding[0].system` / `.code` |
| `category` | `ServiceRequest.category[0].text` |
| `status` | `ServiceRequest.status` |
| `priority` | `ServiceRequest.priority` |
| `authoredOn` | `ServiceRequest.authoredOn` |
| *(fixed)* | `ServiceRequest.intent` = `order` |

---

## Insurance → FHIR Coverage and payer Organization

| Request Field | FHIR Path |
|---|---|
| `patientId` | `Coverage.beneficiary` (Reference `Patient/{id}`) |
| `memberId` | `Coverage.identifier[system=http://praxishealthplan.example/fhir/identifier/member-id]` (type `v2-0203#MB`) and `Coverage.subscriberId` |
| `status` | `Coverage.status` |
| `relationship` *(default `self`)* | `Coverage.relationship.coding[0]` (system `http://terminology.hl7.org/CodeSystem/subscriber-relationship`) |
| `periodStart` / `periodEnd` *(optional)* | `Coverage.period.start` / `.end` |
| `planName` *(optional)* | `Coverage.class[type=plan].value` and `.name` |
| `payerId` + `payerName` | `Coverage.payor[0]`: reference `Organization/{id}`, `identifier` and `display` |

The payer `Organization` is looked up by `identifier=http://praxis.clinic/fhir/identifier/payer|{payerId}` and created when missing:

| Request Field | FHIR Path |
|---|---|
| `payerId` | `Organization.identifier[system=http://praxis.clinic/fhir/identifier/payer]` |
| `payerName` | `Organization.name` |
| *(fixed)* | `Organization.active` = `true`, `Organization.type[0]` = `organization-type#pay` |

The member id system belongs to the payer (`http://praxishealthplan.example/fhir/identifier/member-id`), and the payer HAPI's `Coverage` uses the same identifier, so the two sides match a member by `Coverage.identifier`.

---

## Vitals → FHIR Observation

Each vital becomes a **separate Observation** resource. Blood pressure = 2 observations.

### LOINC Codes

| Vital | LOINC Code | Unit |
|---|---|---|
| Blood Pressure (systolic) | `8480-6` | `mm[Hg]` |
| Blood Pressure (diastolic) | `8462-4` | `mm[Hg]` |
| Weight | `29463-7` | `kg` |
| SpO2 | `59408-5` | `%` |

LOINC system URI: `http://loinc.org`

### Field Mapping

| Request Field | FHIR Path |
|---|---|
| `patientId` | `Observation.subject` (Reference `Patient/{id}`) |
| `encounterId` | `Observation.encounter` (Reference `Encounter/{id}`) |
| `effectiveDate` | `Observation.effectiveDateTime` |
| `systolicBp` / `weightKg` / etc. | `Observation.valueQuantity.value` |
| unit (derived from LOINC code) | `Observation.valueQuantity.unit` + `system=http://unitsofmeasure.org` |
| LOINC code (derived from field name) | `Observation.code.coding[0]` |
| (always) | `Observation.status = "final"` |

```java
// ObservationMapper produces one Observation per non-null vital in the request
public List<Observation> toFhirObservations(VitalsRequest request) {
    List<Observation> observations = new ArrayList<>();
    if (request.systolicBp() != null)
        observations.add(build(request, "systolic", request.systolicBp().doubleValue(), effectiveDate));
    if (request.weightKg() != null)
        observations.add(build(request, "weight", request.weightKg(), effectiveDate));
    // ... diastolicBp, spo2Percent
    return observations;
}
```

---

## Why Separate Observations Per Vital?

This is the FHIR standard. Each `Observation` represents one measurable concept with one LOINC code. Storing multiple vitals in one resource violates FHIR semantics and breaks standard FHIR queries like:

```
GET /fhir/Observation?patient=123&code=29463-7   ← weight only
GET /fhir/Observation?patient=123&code=8480-6    ← systolic only
```
