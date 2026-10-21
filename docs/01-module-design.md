# Module Design

## Package Structure

```
io.github.samarthshubham.clinic
├── config/
│   ├── FhirConfig.java              ← IGenericClient bean, FhirContext bean, WebMvcConfigurer
│   └── FhirHttpMessageConverter.java← Serializes FHIR resources as application/fhir+json
├── controller/
│   ├── PatientController.java
│   ├── EncounterController.java
│   └── VitalsController.java
├── service/
│   ├── PatientService.java
│   ├── EncounterService.java
│   ├── VitalsService.java
│   └── SummaryService.java
├── mapper/
│   ├── PatientMapper.java
│   ├── EncounterMapper.java
│   └── ObservationMapper.java
├── dto/
│   ├── PatientRequest.java
│   ├── EncounterRequest.java
│   └── VitalsRequest.java
└── exception/
    ├── ResourceNotFoundException.java
    └── GlobalExceptionHandler.java
```

## Layer Responsibilities

| Layer | Class | Responsibility |
|---|---|---|
| Controller | `PatientController` | HTTP routing, `@Valid` input validation, `ResponseEntity` shaping |
| Service | `PatientService` | Business logic, orchestrates mapper + FHIR client calls; returns FHIR resources |
| Mapper | `PatientMapper` | Request DTO → FHIR resource conversion (`toFhir` only), no side effects |
| Config | `FhirConfig` | Provides `IGenericClient`, `FhirContext` beans; registers `FhirHttpMessageConverter` |
| DTO | `PatientRequest` | Input contract — validated with `@Valid`, uses Java `record` |
| Exception | `GlobalExceptionHandler` | Translates exceptions to structured `{ status, message }` JSON |

## Response Format

All endpoints return native FHIR R4 resources serialized as `application/fhir+json`:

- Single-resource endpoints (`POST`, `GET /{id}`) → return the resource directly (`Patient`, `Encounter`)
- Collection/search endpoints → return a FHIR `Bundle` (`type: searchset` from HAPI, `type: collection` for aggregations)

Serialization is handled by `FhirHttpMessageConverter`, which uses HAPI's `IParser` (one instance per request — not thread-safe) registered with Spring MVC.

## Core Rule

> **FHIR types (`org.hl7.fhir.r4.model.*`) are the API contract.**
> Request DTOs exist only for `@Valid` input validation. Mappers convert them to FHIR resources. Services and controllers work exclusively with FHIR types.

## FhirConfig

```java
@Configuration
public class FhirConfig implements WebMvcConfigurer {

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

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, new FhirHttpMessageConverter(fhirContext()));
    }
}
```

`IGenericClient` is thread-safe — one shared bean, used across all services.
