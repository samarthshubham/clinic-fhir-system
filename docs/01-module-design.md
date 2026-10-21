# Module Design

## Package Structure

```
io.github.samarthshubham.clinic
├── config/
│   └── FhirConfig.java              ← IGenericClient bean, FhirContext bean
├── controller/
│   ├── PatientController.java
│   ├── EncounterController.java
│   └── VitalsController.java
├── service/
│   ├── PatientService.java
│   ├── EncounterService.java
│   └── VitalsService.java
├── mapper/
│   ├── PatientMapper.java
│   ├── EncounterMapper.java
│   ├── ObservationMapper.java
│   └── PageMapper.java              ← Bundle → PageResponse<T>
├── dto/
│   ├── PatientRequest.java / PatientResponse.java
│   ├── EncounterRequest.java / EncounterResponse.java
│   ├── VitalsRequest.java / ObservationResponse.java
│   └── PageResponse.java            ← { items, total } for list endpoints
└── exception/
    ├── ResourceNotFoundException.java
    └── GlobalExceptionHandler.java
```

## Layer Responsibilities

| Layer | Class | Responsibility |
|---|---|---|
| Controller | `PatientController` | HTTP routing, `@Valid` input validation, `ResponseEntity` shaping; never imports `org.hl7.fhir` |
| Service | `PatientService` | Business logic, orchestrates mapper + FHIR client calls; returns response DTOs to controllers and FHIR resources to other services (`findPatient`) |
| Mapper | `PatientMapper` | `toFhir(request)` and `toResponse(resource)`, no side effects |
| Config | `FhirConfig` | Provides `IGenericClient`, `FhirContext` beans |
| DTO | `PatientRequest` / `PatientResponse` | Input and output contracts, Java `record`s; requests are validated with `@Valid` |
| Exception | `GlobalExceptionHandler` | Translates exceptions to structured `{ status, message }` JSON |

## Response Format

All endpoints return plain JSON (`application/json`) serialized by Spring's default Jackson converter:

- Single-resource endpoints (`POST`, `GET /{id}`) → a flat response record (`PatientResponse`, `EncounterResponse`, …) whose references are flattened to ids (`patientId`, `encounterId`, …)
- List endpoints → `PageResponse<T>` `{ "items": [...], "total": n }`, built from the HAPI search result by `PageMapper`. `items` is the first page of HAPI results and `total` is HAPI's total; there is no paging yet
- `POST /api/vitals` → a JSON array of `ObservationResponse`, one per recorded vital

## Core Rule

> **FHIR types (`org.hl7.fhir.r4.model.*`) stay behind the service layer.**
> Controllers and DTOs never depend on them; `ArchitectureTest` (ArchUnit) fails the build if they do. Mappers convert request DTOs to FHIR resources and FHIR resources back to response DTOs.

## FhirConfig

```java
@Configuration
public class FhirConfig {

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public IGenericClient fhirClient(FhirContext ctx,
                                      @Value("${fhir.server.url}") String url) {
        IGenericClient client = ctx.newRestfulGenericClient(url);
        client.registerInterceptor(new LoggingInterceptor());
        return client;
    }
}
```

`IGenericClient` is thread-safe — one shared bean, used across all services.
