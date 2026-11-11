# Golden Da Vinci fixtures

Shared wire-format examples for clinic-api and payer-api ([test strategy §2](../../docs/16-payer-test-strategy.md)). Both Maven modules put `fixtures/` on the test classpath, so tests load them as `/fixtures/davinci/<folder>/<file>.json`. The CI job `ig-validate` (`.github/scripts/ig-validate.sh`) validates every file here, plus the FHIR that mapper tests write to `target/fhir-out/`, against the IG packages in `infra/docker-compose.yml`, checking codes with tx.fhir.org. The few validator messages it suppresses are listed, with the reason, in `.github/scripts/ig-validate-advisor.txt`.

Changing a golden file is a contract change; say so in the PR.

## Provenance

Each file starts from an example in the published IG package (all CC0-1.0) and is adapted to the demo identities: Praxis Health Plan (`http://praxishealthplan.example/fhir`), member ids `PHP-#####`, Luhn-valid fictional NPIs, and the canonical order codes in [docs/11 §6](../../docs/11-payer-architecture.md#6-coverage-rules).

| File | Source example | Package |
|---|---|---|
| `crd/coverage.json` | `Coverage/example` | hl7.fhir.us.davinci-crd#2.2.1 |
| `crd/service-request.json` | `ServiceRequest/example` | hl7.fhir.us.davinci-crd#2.2.1 |
| `dtr/questionnaire-package-input.json` | `Parameters/QuestionnairePackageInputParamsExample` | hl7.fhir.us.davinci-dtr#2.2.0 |
| `dtr/questionnaire-package-output-cgm.json` | `Parameters/QuestionnairePackageOutputParamsExample`; payer-api's answer to `questionnaire-package-input.json` | hl7.fhir.us.davinci-dtr#2.2.0 |
| `dtr/questionnaire-*.json`, `dtr/library-*.json`, `dtr/valueset-*.json` | `Questionnaire/home-o2-std-questionnaire`, written for the four §6 orders; SNOMED codes checked with tx.fhir.org | hl7.fhir.us.davinci-dtr#2.2.0 |
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

## Running the validator locally

```bash
bash .github/scripts/ig-validate.sh                    # fixtures + both modules' target/fhir-out
bash .github/scripts/ig-validate.sh fixtures/davinci/pas
```

The first run downloads `validator_cli.jar` (checked against a pinned SHA-256) and the IG packages into `~/.fhir/packages`, which takes a few minutes.
