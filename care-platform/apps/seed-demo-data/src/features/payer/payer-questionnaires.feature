Feature: Seed payer — DTR questionnaires, libraries and value sets

  The DTR documentation forms Praxis Health Plan asks for on the orders that
  need prior authorization with documentation (docs/11 §6), taken from the
  golden fixtures in fixtures/davinci/dtr. Each resource is PUT under its fixed
  id, so a rerun with unchanged fixtures leaves the payer HAPI as it is, and a
  changed fixture becomes a new version. payer-api's $questionnaire-package
  then has to find each form from its order code.

  Background: payer-api knows the seed client
    Given the seed has a payer-api system token

  Scenario: Seed the DTR questionnaire packages
    Given the payer HAPI has these DTR fixtures:
      | file                                            |
      | valueset-lumbar-spine-indications.json          |
      | valueset-sleep-study-comorbidities.json         |
      | valueset-diabetes-types.json                    |
      | valueset-echocardiogram-indications.json        |
      | library-mri-lumbar-spine.json                   |
      | library-polysomnography.json                    |
      | library-continuous-glucose-monitor.json         |
      | library-echocardiogram.json                     |
      | questionnaire-mri-lumbar-spine.json             |
      | questionnaire-polysomnography.json              |
      | questionnaire-continuous-glucose-monitor.json   |
      | questionnaire-echocardiogram.json               |
    Then payer-api returns these questionnaire packages:
      | order                      | system                                                  | code     | questionnaire                                                                   |
      | MRI lumbar spine           | http://www.ama-assn.org/go/cpt                          | 72148    | http://praxishealthplan.example/fhir/Questionnaire/mri-lumbar-spine           |
      | Polysomnography            | http://snomed.info/sct                                  | 60554003 | http://praxishealthplan.example/fhir/Questionnaire/polysomnography            |
      | Continuous glucose monitor | http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets | E2103    | http://praxishealthplan.example/fhir/Questionnaire/continuous-glucose-monitor |
      | Echocardiogram             | http://snomed.info/sct                                  | 40701008 | http://praxishealthplan.example/fhir/Questionnaire/echocardiogram             |
      | CBC                        | http://loinc.org                                        | 58410-2  |                                                                                 |
