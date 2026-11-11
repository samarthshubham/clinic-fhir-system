# Golden Da Vinci fixtures

Shared wire-format examples for clinic-api and payer-api ([test strategy §2](../../docs/16-payer-test-strategy.md)). Both Maven modules put `fixtures/` on the test classpath, so tests load them as `/fixtures/davinci/<folder>/<file>.json`. The CI job `ig-validate` (`.github/scripts/ig-validate.sh`) validates every file here, plus the FHIR that mapper tests write to `target/fhir-out/`, against the IG packages in `infra/docker-compose.yml`, checking codes with tx.fhir.org. The few validator messages it suppresses are listed, with the reason, in `.github/scripts/ig-validate-advisor.txt`.

Changing a golden file is a contract change; say so in the PR.

## Provenance

Each file starts from an example in the published IG package (all CC0-1.0) and is adapted to the demo identities: Praxis Health Plan (`http://praxishealthplan.example/fhir`), member ids `PHP-#####`, Luhn-valid fictional NPIs, and the canonical order codes in [docs/11 §6](../../docs/11-payer-architecture.md#6-coverage-rules).

| File | Source example | Package |
|---|---|---|
| `crd/coverage.json` | `Coverage/example` | hl7.fhir.us.davinci-crd#2.2.1 |
| `crd/service-request.json` | `ServiceRequest/example` | hl7.fhir.us.davinci-crd#2.2.1 |
| `crd/order-sign-request.json` | `Binary/CRDServiceRequest` (order-sign hook request), with an MRI lumbar spine order for Sandra Okafor | hl7.fhir.us.davinci-crd#2.2.1 |
| `crd/order-sign-request-covered.json` | the same request with a CBC order for Priya Nair | hl7.fhir.us.davinci-crd#2.2.1 |
| `crd/order-sign-response-pa-needed.json` | `Binary/CRDServiceResponse3` (coverage-information `systemAction`), payer-api's answer to `order-sign-request.json` | hl7.fhir.us.davinci-crd#2.2.1 |
| `crd/order-sign-response-covered.json` | `Binary/CRDServiceResponse3`, payer-api's answer to `order-sign-request-covered.json` | hl7.fhir.us.davinci-crd#2.2.1 |
| `crd/appointment-book-request.json` | `crd/order-sign-request.json` with the MRI as a proposed `Appointment` (`profile-appointment`) in `context.appointments` | hl7.fhir.us.davinci-crd#2.2.1 |
| `crd/appointment-book-response-pa-needed.json` | `Binary/CRDServiceResponse3`, payer-api's answer to `appointment-book-request.json` | hl7.fhir.us.davinci-crd#2.2.1 |
| `crd/encounter-start-request.json` | `crd/order-sign-request.json` with an in-progress MRI `Encounter` (`profile-encounter`) in `prefetch.encounter` | hl7.fhir.us.davinci-crd#2.2.1 |
| `crd/encounter-start-response-pa-needed.json` | `Binary/CRDServiceResponse3`, payer-api's answer to `encounter-start-request.json` | hl7.fhir.us.davinci-crd#2.2.1 |
| `crd/order-dispatch-request.json` | `crd/order-sign-request.json` with the MRI order active and dispatched to an imaging center, in `prefetch.dispatchedOrders` | hl7.fhir.us.davinci-crd#2.2.1 |
| `crd/order-dispatch-response-pa-needed.json` | `Binary/CRDServiceResponse3`, payer-api's answer to `order-dispatch-request.json` | hl7.fhir.us.davinci-crd#2.2.1 |
| `dtr/questionnaire-package-input.json` | `Parameters/QuestionnairePackageInputParamsExample` | hl7.fhir.us.davinci-dtr#2.2.0 |
| `pas/claim-request-bundle.json` | `Bundle/ReferralAuthorizationBundleExample` | hl7.fhir.us.davinci-pas#2.2.1 |
| `pas/claim-response-pended-bundle.json` | `Bundle/ReferralPendingAuthorizationResponseBundleExample` | hl7.fhir.us.davinci-pas#2.2.1 |
| `cdex/task-attachment-request.json` | `Task/cdex-task-example19` | hl7.fhir.us.davinci-cdex#2.1.0 |
| `cdex/submit-attachment-parameters.json` | `Parameters/cdex-parameters-example4` | hl7.fhir.us.davinci-cdex#2.1.0 |
| `pdex/prior-authorization-eob.json` | `ExplanationOfBenefit/PDexPriorAuth1` | hl7.fhir.us.davinci-pdex#2.2.0 |
| `pdex/member-match-in.json` | `Parameters/payer-multi-member-match-in` (one member) | hl7.fhir.us.davinci-pdex#2.2.0 |
| `plan-net/organization-payer.json` | `Organization/Acme` | hl7.fhir.us.davinci-pdex-plan-net#1.2.0 |
| `plan-net/insurance-plan.json` | `InsurancePlan/AcmeQHPGold` | hl7.fhir.us.davinci-pdex-plan-net#1.2.0 |
| `plan-net/practitioner.json` | `Practitioner/JoeSmith` | hl7.fhir.us.davinci-pdex-plan-net#1.2.0 |
| `formulary/formulary.json` | `InsurancePlan/FormularyD1002` | hl7.fhir.us.davinci-drug-formulary#2.1.0 |
| `formulary/formulary-drug.json` | `MedicationKnowledge/FormularyDrug-1000091` | hl7.fhir.us.davinci-drug-formulary#2.1.0 |
| `formulary/formulary-item.json` | `Basic/FormularyItem-D1002-1000091` | hl7.fhir.us.davinci-drug-formulary#2.1.0 |

`provider/` holds what clinic-api builds for its own FHIR server (E0-S5).

The `crd/order-sign-*`, `crd/order-dispatch-*`, `crd/appointment-book-*` and `crd/encounter-start-*` files are CDS Hooks JSON, not FHIR resources. `GoldenFixturesTest` and `ig-validate` check the FHIR resources inside them (draft orders, prefetch and the updated orders in `systemActions`). `CdsHooksInteropTest` posts each request to payer-api and expects exactly the matching response; it writes what payer-api answered to `payer-api/target/cds-out/` for diffing. The responses are dated 2026-11-11, the test's fixed clock.

## Running the validator locally

```bash
bash .github/scripts/ig-validate.sh                    # fixtures + both modules' target/fhir-out
bash .github/scripts/ig-validate.sh fixtures/davinci/pas
```

The first run downloads `validator_cli.jar` (checked against a pinned SHA-256) and the IG packages into `~/.fhir/packages`, which takes a few minutes.
