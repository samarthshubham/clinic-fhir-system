# Clinic FHIR System

[![CI](https://github.com/samarthshubham/clinic-fhir-system/actions/workflows/ci.yml/badge.svg)](https://github.com/samarthshubham/clinic-fhir-system/actions/workflows/ci.yml)

> **License notice:** This repository is provided for **viewing and evaluation purposes only.**
> No permission is granted to copy, modify, distribute, sublicense, or use the code in any form
> without explicit written permission from the author. See [LICENSE](./LICENSE) for full terms.

A production-style, FHIR-compliant clinical management system for a small clinic. Built for learning and portfolio purposes using Java 21, Spring Boot, HAPI FHIR, and Angular 21.

## Architecture

```
Angular (care-platform/)
        ↓  REST JSON  (proxy → localhost:9090)
clinic-api  (Spring Boot — business logic, DTO↔FHIR mapping)
        ↓  FHIR REST (HTTP/JSON)
HAPI FHIR JPA Server  (FHIR resource storage + validation)
        ↓  JPA/JDBC
PostgreSQL 15  (managed entirely by HAPI)
```

## Repository Layout

| Folder | Stack | Purpose |
|---|---|---|
| `clinic-api/` | Java 21, Spring Boot 3.5 | Backend REST API + FHIR integration |
| `care-platform/` | Angular 21, Nx monorepo | `clinician-app` (active) · `patient-app` (scaffolded) |
| `infra/` | Docker Compose | HAPI FHIR JPA Server + PostgreSQL 15 + Prometheus + Grafana |
| `docs/` | Markdown | Architecture, API reference, design docs |

---

## Quick Start

### Prerequisites

- Java 21
- Docker Desktop
- Node.js 18+

### Clone and install hooks

```bash
git clone <repo-url>
cd clinic-fhir-system
npm install   # installs Husky pre-commit hooks
chmod +x dev.sh swagger.sh clinic-api/mvnw clinic-api/seed-data/load.sh   # make scripts executable
```

### Start everything

```bash
./dev.sh           # restart (default) — stops anything running, then starts fresh
./dev.sh start     # start only if nothing is running
./dev.sh stop      # stop all services
./dev.sh restart   # stop then start
```

Services start in the correct order, each waiting for the previous to be healthy:

| Service | URL |
|---|---|
| PostgreSQL 15 | `localhost:5432` |
| HAPI FHIR JPA Server | `http://localhost:8080/fhir` |
| clinic-api (Spring Boot) | `http://localhost:9090` |
| clinician-app (Angular) | `http://localhost:4200` |
| Swagger UI | `http://localhost:8081` |
| Prometheus | `http://localhost:9091` |
| Grafana | `http://localhost:3000` (login `admin` / `admin`) |

Grafana loads the **Clinic → clinic-api** dashboard (request rate, p50/p95/p99 latency, 4xx and 5xx rates per endpoint) and a 5xx error rate alert from `infra/grafana/`. Prometheus scrapes `http://localhost:9090/actuator/prometheus` every 5s. The `admin` / `admin` login is for local development only; skip the password change prompt on first login.

### Demo logins (dev only)

The API and clinician-app require a login. These accounts live in the `dev` Spring profile (`clinic-api/src/main/resources/application-dev.yaml`), which `./mvnw spring-boot:run` and `dev.sh` turn on automatically. In development builds of clinician-app, the login page has a one-click button for each account.

| Username | Password | Role | Can |
|---|---|---|---|
| `admin` | `Admin#2026` | `ADMIN` | everything, including registering practitioners |
| `clinician` | `Clinician#2026` | `CLINICIAN` | all clinical reads and writes |
| `frontdesk` | `FrontDesk#2026` | `FRONT_DESK` | register patients, book appointments; no clinical data |

A packaged jar (`java -jar`) does not use the `dev` profile, so it has no demo users and refuses to start until `CLINIC_JWT_SECRET` (at least 32 bytes) is set; provide real users through `clinic.auth.users`. Production builds of clinician-app swap the quick-login accounts for an empty list (`fileReplacements`), so the passwords never reach the bundle. In Swagger UI, call `POST /api/auth/login`, then click **Authorize** and paste the `accessToken`. See [docs/10-auth.md](./docs/10-auth.md).

> **First boot:** HAPI FHIR takes up to ~5 minutes to initialize on a fresh volume. Subsequent starts are much faster.

Logs are written to `./logs/` in plain text (no ANSI codes). Press **Ctrl+C** in the `dev.sh` terminal to stop all services cleanly — it kills background processes and runs `docker compose down`.

To follow a service live, open a dedicated terminal for each one you care about:

**Terminal 1 — HAPI FHIR (Docker)**
```bash
docker compose -f infra/docker-compose.yml logs -f hapi-fhir
```

**Terminal 2 — Spring Boot backend**
```bash
tail -f logs/clinic-api.log
```

**Terminal 3 — Angular clinician app**
```bash
tail -f logs/clinician-app.log
```

<!--
**Terminal 4 — Angular patient app** (uncomment in dev.sh first)
```bash
tail -f logs/patient-app.log
```
-->

---

## Manual Start (alternative)

If you prefer to start services individually:

**1. Infrastructure**
```bash
docker compose -f infra/docker-compose.yml up -d
```
HAPI FHIR takes ~60–90s to initialize. Check health:
```bash
docker compose -f infra/docker-compose.yml ps
```

**2. Backend**
```bash
cd clinic-api && ./mvnw spring-boot:run
```

**3. Frontend**
```bash
cd care-platform
npx nx serve clinician-app --port=4200
# npx nx serve patient-app --port=4201
```

**Stop infrastructure:**
```bash
docker compose -f infra/docker-compose.yml down      # stop, keep data
docker compose -f infra/docker-compose.yml down -v   # stop and wipe data
```

---

## Code Formatting

The pre-commit hook formats staged files automatically on every `git commit`.

To format manually:

```bash
# Java (Google style via Spotless)
cd clinic-api && ./mvnw spotless:apply

# Angular (Prettier — run from care-platform/)
npx nx run clinician-app:lint --fix
```

## Tests and Coverage

```bash
cd clinic-api && ./mvnw verify                                          # JaCoCo report: clinic-api/target/site/jacoco/index.html
cd care-platform && npx nx test clinician-app --coverage --watch=false  # report: care-platform/coverage/clinician-app/
```

---

## Pull Request Checks

A pull request can be merged into `main` only when:

- **CI passes**: formatting, backend tests, frontend tests and build, and lint on changed frontend files.
- **Changed lines are at least 70% covered by tests** (`coverage-gate`, using [diff-cover](https://github.com/Bachmann1234/diff_cover)). The job summary lists any uncovered new lines.

Claude Code also reviews each pull request once when it is opened or marked ready for review, and leaves inline comments on any issues it finds. The review is advisory: address the comments, then merge. Draft PRs and PRs with `WIP` in the title are skipped.

- Mention `@claude` in a PR or issue comment to ask a question or request a change.
- Comment `@claude review` to request another review after new commits.

---

## Features

- Register and manage patients (FHIR `Patient`)
- Record patient visits / encounters (FHIR `Encounter`)
- Store vitals — Blood Pressure, Weight, SpO2 (FHIR `Observation` with LOINC codes)
- Patient summary aggregation endpoint
- Patient roster UI with search (`clinician-app`)
- Patient detail page with ApexCharts vitals timeline (`clinician-app`)
- Cucumber-driven seed data app — 5 idempotent patient profiles with relative dates (`seed-demo-data`)
- Full FHIR R4 compliance via HAPI FHIR
- JWT login with role-based access control (`ADMIN`, `CLINICIAN`, `FRONT_DESK`) — see [auth](./docs/10-auth.md)
- Clinician alerts for critical systolic BP via FHIR Subscriptions: stored as FHIR `Flag`s, shown in the clinician-app Inbox with a live badge, plus a mock SMS in the log — see [demo](./docs/09-bp-alerts-demo.md)

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5, HAPI FHIR Client 7.4 |
| FHIR Server | HAPI FHIR JPA Server v7.4.0 (R4) |
| Database | PostgreSQL 15 (owned by HAPI) |
| Frontend | Angular 21, Angular Material M3, Nx monorepo |
| Infra | Docker Compose |

---

## Docs

See [`docs/`](./docs) for full system design:

- [Architecture](./docs/00-architecture.md)
- [Module Design](./docs/01-module-design.md)
- [API Design](./docs/02-api-design.md)
- [FHIR Mapping](./docs/03-fhir-mapping.md)
- [Key Flows](./docs/04-key-flows.md)
- [Database Internals](./docs/05-database.md)
- [MVP Plan](./docs/06-mvp-plan.md)
- [Best Practices](./docs/07-best-practices.md)
- [Design System](./docs/08-design-system.md)
- [High BP Alerts Demo](./docs/09-bp-alerts-demo.md)
- [Authentication & RBAC](./docs/10-auth.md)

---

## License

**Proprietary Evaluation License** — Copyright (c) 2026 samarthshubham (Shubham Samarth). All rights reserved.

This repository is provided for viewing and evaluation purposes only. No permission is granted to copy, modify, distribute, sublicense, or use this code in any form without explicit written permission from the author.

See [LICENSE](./LICENSE) for full terms. For permissions, contact: shubhamsamarth.dev@gmail.com
