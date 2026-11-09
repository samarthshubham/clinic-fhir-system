Feature: Seed Patient — Marcus Webb (hypertensive)

  60-year-old hypertensive male with progressive BP improvement on medication.
  Demonstrates: BP trends (critical → warning → improving), 6-encounter timeline,
  HR vitals, 2 pending orders (Lipid Panel + Echocardiogram urgent), 2 conditions,
  4 medications, 3-goal care plan.

  Background: Infrastructure provisioned
    Given practitioner "seed-default-practitioner" is provisioned
    And organization "seed-default-org" is provisioned

  Scenario: Seed Marcus Webb
    Given a patient exists with the following details:
      | id          | seed-marcus-webb        |
      | firstName   | Marcus                  |
      | lastName    | Webb                    |
      | dateOfBirth | 1965-03-12              |
      | gender      | male                    |
      | phone       | 5551001001              |
      | email       | marcus.webb@example.com |
      | mrn         | PX-10000004             |

    And the patient is covered by Praxis Health Plan:
      | memberId       | PHP-10004       |
      | planName       | Praxis Gold PPO |
      | status         | active          |
      | startedDaysAgo | 365             |

    # Encounter 1 — initial presentation, severe uncontrolled hypertension
    When an encounter is recorded 90 days ago with reason "Initial hypertension evaluation" and note "BP 182/114 at rest — significantly elevated. No prior treatment. Lisinopril initiated. Lifestyle counselling given. Lab work ordered." and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp         | 182  |
      | diastolicBp        | 114  |
      | weightKg           | 93   |
      | heartRateBpm       | 88   |
      | temperatureCelsius | 37.0 |

    # Encounter 2 — first follow-up, partial medication response
    When an encounter is recorded 60 days ago with reason "Hypertension follow-up — medication titration" and note "BP improving but still elevated. Added Amlodipine 5mg. Weight stable. Patient reports good medication adherence. Atorvastatin added for elevated LDL noted on labs." and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 170 |
      | diastolicBp  | 108 |
      | weightKg     | 92  |
      | heartRateBpm | 84  |

    # Encounter 3 — continued titration
    When an encounter is recorded 42 days ago with reason "Hypertension follow-up" and note "Dual therapy showing effect. BP trending down. Weight slightly decreased. Continue current regimen." and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 158 |
      | diastolicBp  | 98  |
      | weightKg     | 91  |
      | heartRateBpm | 78  |

    # Encounter 4 — medication adjustment (transient increase during dose change)
    When an encounter is recorded 28 days ago with reason "BP medication adjustment" and note "Transient BP increase during Amlodipine dose increase. Expected response. Aspirin added for cardioprotection. BMP ordered to monitor renal function." and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 162 |
      | diastolicBp  | 101 |
      | weightKg     | 90  |
      | heartRateBpm | 76  |

    And a service request is placed with code "Basic Metabolic Panel" category "laboratory" authored 28 days ago

    # Encounter 5 — steady improvement
    When an encounter is recorded 14 days ago with reason "BP medication adjustment — improvement noted" and note "BP coming down steadily. Dual therapy effective. BMP results reviewed — no renal concerns. Continue plan." and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 148 |
      | diastolicBp  | 94  |
      | weightKg     | 89  |
      | heartRateBpm | 74  |

    # BMP results arrived 13 days after order
    And a diagnostic report is created from the last service request with title "Basic Metabolic Panel" issued 15 days ago
    And the last diagnostic report conclusion is "Electrolytes within normal range. Creatinine 1.1 mg/dL. eGFR 81 — stable. No acute findings."

    # Encounter 6 — most recent, still mildly elevated (warning range)
    When an encounter is recorded 3 days ago with reason "BP check — approaching target" and note "Good progress. BP 141/89 — near target range. HR normalising. Weight at 89 kg — above 88 kg goal. Lipid Panel and Echo ordered to assess cardiovascular status." and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp         | 141  |
      | diastolicBp        | 89   |
      | weightKg           | 89   |
      | heartRateBpm       | 72   |
      | temperatureCelsius | 36.7 |

    # Pending orders — no DiagnosticReport yet (active = shows in pending orders panel)
    And a service request is placed with code "Lipid Panel" category "laboratory" authored 3 days ago
    And a service request is placed with code "Echocardiogram" category "imaging" priority "urgent" authored 3 days ago

    # Clinical resources
    And a condition is recorded with code "59621000" display "Essential hypertension" status "active" onset 400 days ago
    And a condition is recorded with code "55822004" display "Hyperlipidemia" status "active" onset 180 days ago
    And a medication is recorded with name "Lisinopril 10mg" status "active" dosage "Once daily" started 90 days ago
    And a medication is recorded with name "Amlodipine 5mg" status "active" dosage "Once daily" started 60 days ago
    And a medication is recorded with name "Atorvastatin 20mg" status "active" dosage "Once nightly" started 60 days ago
    And a medication is recorded with name "Aspirin 81mg" status "active" dosage "Once daily" started 28 days ago
    And an appointment is scheduled 30 days from now with description "3-month hypertension and lipid review"

    # Goals — 3 goals tracked in care plan
    And a goal is created with description "Reduce systolic BP below 130" target LOINC "8480-6" value 130.0 unit "mm[Hg]" due 90 days from now
    And a goal is created with description "Maintain weight under 88 kg" target LOINC "29463-7" value 88.0 unit "kg" due 90 days from now
    And a goal is created with description "Walk 30 minutes, 5 days per week" due 60 days from now
    And a care plan is created with title "Hypertension Management" addressing all conditions with all goals
