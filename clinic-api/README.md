# clinic-api

Spring Boot backend for the Clinic FHIR System. Exposes a REST API for clinic operations (patients, encounters, vitals) and handles all mapping between internal DTOs and FHIR R4 resources. FHIR storage and validation are delegated to the HAPI FHIR JPA server — this service never touches the database directly.

## Tech Stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| FHIR Client | HAPI FHIR 7.4 (R4) |
| Utilities | Lombok |
| Build | Maven (Maven Wrapper included) |
| Code Style | Google Java Format via Spotless |

## Prerequisites

- Java 21
- HAPI FHIR server running at `http://localhost:8080/fhir` (see [`../infra`](../infra))

## Running

```bash
cd clinic-api
./mvnw spring-boot:run
```

API is available at `http://localhost:9090`.

## Configuration

All config lives in `src/main/resources/application.yaml`:

```yaml
server:
  port: 9090            # API port

fhir:
  server:
    url: http://localhost:8080/fhir   # HAPI FHIR server URL
```

To point at a different FHIR server (e.g. staging), override at runtime:

```bash
./mvnw spring-boot:run -Dfhir.server.url=http://staging-host:8080/fhir
```

## Project Structure

```
clinic-api/
├── integration-tests/   # Postman collection for end-to-end testing
├── seed-data/           # Synthea data generation + HAPI FHIR loader
└── src/main/java/io/github/samarthshubham/clinic/
├── config/        # Spring beans — FHIR client setup
├── controller/    # REST controllers (@RestController)
├── dto/           # Request/response POJOs
├── mapper/        # DTO ↔ FHIR resource conversion
├── service/       # Business logic, calls HAPI FHIR client
└── exception/     # Global exception handling (@ControllerAdvice)
```

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/patients` | Register a new patient |
| `GET` | `/api/patients/{id}` | Get patient by ID |
| `GET` | `/api/patients?name=&dob=` | Search patients |
| `POST` | `/api/encounters` | Create an encounter (visit) |
| `GET` | `/api/encounters/{id}` | Get encounter by ID |
| `GET` | `/api/encounters?patientId=` | List encounters for patient |
| `POST` | `/api/vitals` | Record vitals (BP, weight, SpO2) |
| `GET` | `/api/vitals?patientId=&type=` | Get vitals for patient |

## Building

```bash
./mvnw clean package              # compile + test + package
./mvnw clean package -DskipTests  # skip tests
```

The fat JAR is output to `target/clinic-api-0.0.1-SNAPSHOT.jar`.

## Code Formatting

This project uses **Spotless** with Google Java Format (GOOGLE style).

```bash
./mvnw spotless:apply   # format all Java files
./mvnw spotless:check   # check without modifying (used in CI)
```

The pre-commit hook at the repo root runs `spotless:apply` automatically on staged `.java` files before every commit. See the root [`README`](../README.md) for hook setup.

## Running Tests

```bash
./mvnw test
```
