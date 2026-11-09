Feature: Seed Patient — Priya Nair (healthy, preventive care)

  32-year-old healthy female with seasonal allergies and normal vitals.
  Demonstrates: all-normal vitals, no alerts, 3-encounter timeline, 2 completed
  service request cycles (CBC + Thyroid), 2 achieved goals, no pending orders.

  Background: Infrastructure provisioned
    Given practitioner "seed-default-practitioner" is provisioned
    And organization "seed-default-org" is provisioned

  Scenario: Seed Priya Nair
    Given a patient exists with the following details:
      | id          | seed-priya-nair        |
      | firstName   | Priya                  |
      | lastName    | Nair                   |
      | dateOfBirth | 1992-07-28             |
      | gender      | female                 |
      | phone       | 5551002002             |
      | email       | priya.nair@example.com |
      | mrn         | PX-10000001            |

    # Encounter 1 — annual wellness exam
    When an encounter is recorded 38 days ago with reason "Annual wellness exam" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 112 |
      | diastolicBp  | 72  |
      | weightKg     | 58  |
      | spo2Percent  | 99  |
      | heartRateBpm | 68  |

    # CBC and thyroid panel ordered at wellness visit — both fulfilled (no pending orders)
    And a service request is placed with code "Complete Blood Count" category "laboratory" authored 38 days ago
    And a diagnostic report is created from the last service request with title "Complete Blood Count" issued 30 days ago
    And the last diagnostic report conclusion is "All values within normal range. Haemoglobin 13.5 g/dL. WBC 6.2 × 10³/µL."

    And a service request is placed with code "Thyroid Panel" category "laboratory" authored 38 days ago
    And a diagnostic report is created from the last service request with title "Thyroid Panel" issued 28 days ago
    And the last diagnostic report conclusion is "TSH 2.1 mIU/L — within normal range. Free T4 1.2 ng/dL. No thyroid dysfunction."

    # Encounter 2 — seasonal allergy consultation
    When an encounter is recorded 10 days ago with reason "Seasonal allergy consultation" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 110 |
      | diastolicBp  | 70  |
      | weightKg     | 58  |
      | spo2Percent  | 99  |
      | heartRateBpm | 65  |

    # Encounter 3 — brief follow-up
    When an encounter is recorded 3 days ago with reason "Allergy symptom follow-up — improving" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 112 |
      | diastolicBp  | 72  |
      | weightKg     | 58  |
      | spo2Percent  | 99  |
      | heartRateBpm | 66  |

    # Clinical resources
    And a condition is recorded with code "61582004" display "Seasonal allergic rhinitis" status "active" onset 400 days ago
    And a medication is recorded with name "Cetirizine 10mg" status "active" dosage "Once daily as needed" started 400 days ago
    And an appointment is scheduled 45 days from now with description "Annual physical examination"

    # Both goals achieved — demonstrates empty alert banner and achieved goal states
    And an achieved goal is created with description "Maintain SpO2 above 98%" target LOINC "59408-5" value 98.0 unit "%"
    And an achieved goal is created with description "Annual preventive screening up to date" target LOINC "59408-5" value 98.0 unit "%"
    And a care plan is created with title "Wellness Maintenance" addressing the last condition with all goals
