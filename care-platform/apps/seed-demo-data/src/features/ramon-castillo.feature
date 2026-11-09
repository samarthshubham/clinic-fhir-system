Feature: Seed Patient — Ramon Castillo (T2DM + hypertension + CKD)

  66-year-old male with type 2 diabetes, hypertension, and CKD stage 2 on triple therapy.
  Demonstrates: 3-condition panel, pending lab (Lipid Panel routine) + urgent renal order,
  quarterly review cadence with HR vitals, 2-goal care plan (HbA1c active, BP achieved).

  Background: Infrastructure provisioned
    Given practitioner "seed-default-practitioner" is provisioned
    And organization "seed-default-org" is provisioned

  Scenario: Seed Ramon Castillo
    Given a patient exists with the following details:
      | id          | seed-ramon-castillo        |
      | firstName   | Ramon                      |
      | lastName    | Castillo                   |
      | dateOfBirth | 1958-09-30                 |
      | gender      | male                       |
      | phone       | 5551005005                 |
      | email       | ramon.castillo@example.com |
      | mrn         | PX-10000002                |

    And the patient is covered by Praxis Health Plan:
      | memberId       | PHP-10002       |
      | planName       | Praxis Gold PPO |
      | status         | active          |
      | startedDaysAgo | 365             |

    # HbA1c ordered at the 30-day visit (will be completed by a report below)
    And a service request is placed with code "HbA1c Panel" category "laboratory" authored 30 days ago

    # Encounter 1 — quarterly review
    When an encounter is recorded 40 days ago with reason "Diabetes management — quarterly review" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 136 |
      | diastolicBp  | 86  |
      | weightKg     | 98  |
      | spo2Percent  | 96  |
      | heartRateBpm | 84  |

    # Encounter 2 — Metformin dose review
    When an encounter is recorded 25 days ago with reason "Metformin dose review" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 133 |
      | diastolicBp  | 84  |
      | weightKg     | 97  |
      | spo2Percent  | 96  |
      | heartRateBpm | 82  |

    # HbA1c result — fulfils the service request ordered 30 days ago
    And a diagnostic report is created from the last service request with title "HbA1c and Metabolic Panel" issued 15 days ago
    And the last diagnostic report conclusion is "HbA1c 7.2% — controlled but above 7.0% target. LDL 98 mg/dL. eGFR 68 — mild CKD, monitor closely. Continue current regimen."

    # Encounter 3 — foot exam
    When an encounter is recorded 12 days ago with reason "Foot exam and neuropathy screen" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp         | 130  |
      | diastolicBp        | 82   |
      | weightKg           | 96   |
      | spo2Percent        | 97   |
      | heartRateBpm       | 85   |
      | temperatureCelsius | 37.1 |

    # Encounter 4 — HbA1c results review, most recent
    When an encounter is recorded 1 days ago with reason "HbA1c results review — management escalation" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 128 |
      | diastolicBp  | 80  |
      | weightKg     | 95  |
      | spo2Percent  | 97  |
      | heartRateBpm | 82  |

    # Pending orders — Lipid Panel (routine) + Renal Function Panel (urgent)
    # No DiagnosticReports → both stay active in the pending orders panel
    And a service request is placed with code "Lipid Panel" category "laboratory" authored 1 days ago
    And a service request is placed with code "Renal Function Panel" category "laboratory" priority "urgent" authored 1 days ago

    # Clinical resources — 3 conditions, 4 medications
    And a condition is recorded with code "44054006" display "Type 2 diabetes mellitus" status "active" onset 2200 days ago
    And a condition is recorded with code "59621000" display "Essential hypertension" status "active" onset 1400 days ago
    And a condition is recorded with code "431857002" display "Chronic kidney disease stage 2" status "active" onset 365 days ago
    And a medication is recorded with name "Metformin 1000mg" status "active" dosage "Twice daily with meals" started 2200 days ago
    And a medication is recorded with name "Enalapril 5mg" status "active" dosage "Once daily" started 1400 days ago
    And a medication is recorded with name "Atorvastatin 20mg" status "active" dosage "Once nightly" started 1400 days ago
    And a medication is recorded with name "Dapagliflozin 10mg" status "active" dosage "Once daily in the morning" started 365 days ago
    And an appointment is scheduled 21 days from now with description "Quarterly diabetes and cardiovascular review"

    # Goal 1 — HbA1c active (7.2% current, target 7.0% — above target, on watch)
    And a goal is created with description "Reduce HbA1c below 7.0%" target LOINC "4548-4" value 7.0 unit "%" due 180 days from now
    # Goal 2 — BP achieved (latest systolic 128 < 130 target)
    And an achieved goal is created with description "Maintain systolic BP below 130 mmHg" target LOINC "8480-6" value 130.0 unit "mm[Hg]"
    And a care plan is created with title "Diabetes and Hypertension Management" addressing all conditions with all goals
