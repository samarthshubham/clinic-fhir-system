Feature: Seed payer — Praxis drug formulary

  Publish the drugs in the seed profiles with fictional tier, authorization and
  quantity-limit values. The Praxis Gold PPO plan references this formulary.
  CPAP therapy is a device treatment, outside the drug formulary.

  Scenario: Publish the Praxis drug formulary
    Given the payer HAPI has the Praxis formulary
    And the Praxis formulary covers these RxNorm drugs:
      | drug                                                 | rxcui   | doseForm | tier          | priorAuth | quantityLimit          |
      | lisinopril 10 MG Oral Tablet                         | 314076  | 317541   | generic       | false     |                        |
      | amlodipine 5 MG Oral Tablet                          | 197361  | 317541   | generic       | false     |                        |
      | atorvastatin 20 MG Oral Tablet                       | 617310  | 317541   | generic       | false     |                        |
      | aspirin 81 MG Delayed Release Oral Tablet             | 308416  | 10312    | generic       | false     |                        |
      | metformin hydrochloride 1000 MG Oral Tablet           | 861004  | 317541   | generic       | false     | 60 tablets per 30 days |
      | enalapril maleate 5 MG Oral Tablet                    | 858813  | 317541   | generic       | false     |                        |
      | dapagliflozin 10 MG Oral Tablet                       | 1488569 | 317541   | non-preferred | true      | 30 tablets per 30 days |
      | labetalol hydrochloride 5 MG/ML Injectable Solution   | 896771  | 316949   | generic       | false     |                        |
      | amlodipine 10 MG Oral Tablet                         | 308135  | 317541   | generic       | false     |                        |
      | ibuprofen 400 MG Oral Tablet                         | 197805  | 317541   | generic       | false     |                        |
      | naproxen 500 MG Oral Tablet                          | 198014  | 317541   | generic       | false     |                        |
      | carvedilol 25 MG Oral Tablet                         | 200033  | 317541   | generic       | false     |                        |
      | hydralazine hydrochloride 50 MG Oral Tablet           | 905395  | 317541   | generic       | false     |                        |
      | cetirizine hydrochloride 10 MG Oral Tablet            | 1014678 | 317541   | generic       | false     |                        |
    Then the public formulary has these search matches:
      | type                | query                                                                                               | id                         |
      | InsurancePlan       | type=http://terminology.hl7.org/CodeSystem/v3-ActCode%7CDRUGPOL&identifier=PHP-F-2026                  | praxis-formulary-2026      |
      | MedicationKnowledge | status=active&drug-name=metformin&doseform=317541                                                    | praxis-rx-861004           |
      | Basic               | formulary=InsurancePlan/praxis-formulary-2026&drug-tier=non-preferred&subject:MedicationKnowledge.code=1488569&_include=Basic:subject | praxis-rx-1488569-coverage |
      | Basic               | formulary=InsurancePlan/praxis-formulary-2026&drug-tier=non-preferred&subject:MedicationKnowledge.code=1488569&_include=Basic:subject | praxis-rx-1488569          |
