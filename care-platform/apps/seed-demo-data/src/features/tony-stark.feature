Feature: Seed Patient — Tony Stark (hypertensive crisis)

  58-year-old male engineer with cardiac implant presenting with hypertensive crisis,
  tachycardia, and hypoxia. Demonstrates: critical + warning alerts (2 red pips + 2 amber),
  three flagged vital tiles (BP, HR, SpO₂), 4 active conditions, 4 medications, 3 pending
  cardiac orders, completed lab report, active care plan.

  Background: Infrastructure provisioned
    Given practitioner "seed-default-practitioner" is provisioned
    And organization "seed-default-org" is provisioned

  Scenario: Seed Tony Stark
    Given a patient exists with the following details:
      | id          | seed-tony-stark          |
      | firstName   | Tony                     |
      | lastName    | Stark                    |
      | dateOfBirth | 1970-05-29               |
      | gender      | male                     |
      | phone       | 5559871001               |
      | email       | tony.stark@stark-ind.com |
      | mrn         | PX-10000006              |

    # Visit 1 — initial cardiac evaluation, baseline vitals
    When an encounter is recorded 90 days ago with reason "Cardiac evaluation — implant check" and note "Initial evaluation. BP elevated despite current medication. Arc reactor causing EMI with standard cardiac monitoring. CMP ordered to assess renal and electrolyte status." and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp         | 155  |
      | diastolicBp        | 96   |
      | weightKg           | 82   |
      | spo2Percent        | 96   |
      | heartRateBpm       | 92   |
      | temperatureCelsius | 36.9 |
    And a service request is placed with code "Comprehensive Metabolic Panel" category "laboratory" authored 90 days ago
    And a diagnostic report is created from the last service request with title "Comprehensive Metabolic Panel" issued 85 days ago
    And the last diagnostic report conclusion is "BUN 22 mg/dL. Creatinine 1.3 mg/dL — mildly elevated, monitor for ACE inhibitor effect. Potassium 3.6 mEq/L. No acute electrolyte disturbances."

    # Visit 2 — BP worsening, medication adjusted
    When an encounter is recorded 60 days ago with reason "BP medication adjustment — worsening control" and note "BP worsening. Increased fatigue and exertional dyspnea reported. HR borderline elevated at 98 bpm. Amlodipine dose maximised; Carvedilol initiated for combined BP and rate control." and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 162  |
      | diastolicBp  | 100  |
      | weightKg     | 81.5 |
      | spo2Percent  | 95   |
      | heartRateBpm | 98   |

    # Visit 3 — tachycardia and hypoxia develop
    When an encounter is recorded 30 days ago with reason "Worsening symptoms — tachycardia and hypoxia" and note "Tachycardia confirmed at 105 bpm. SpO₂ declined to 94% — hypoxia developing. Hydralazine added as third-line agent. Patient advised to reduce workload and return urgently if symptoms worsen." and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp         | 170  |
      | diastolicBp        | 104  |
      | weightKg           | 81   |
      | spo2Percent        | 94   |
      | heartRateBpm       | 105  |
      | temperatureCelsius | 37.0 |

    # Visit 4 — hypertensive crisis (most recent — drives snapshot panel alerts)
    # systolic 182 > critical 180, diastolic 122 > critical 120, HR 112 > 100, SpO₂ 93 < warn 95
    When an encounter is recorded 5 days ago with reason "Hypertensive crisis — emergency presentation" and note "Hypertensive crisis: BP 182/122 mmHg, HR 112 bpm, SpO₂ 93%. Emergency cardiology referral placed. Three pending orders: ECG, stress test, echocardiogram. Patient admitted for observation and IV antihypertensive titration." and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp         | 182  |
      | diastolicBp        | 122  |
      | weightKg           | 81   |
      | spo2Percent        | 93   |
      | heartRateBpm       | 112  |
      | temperatureCelsius | 37.3 |

    # Pending cardiac workup — all active (no reports), shows in pending orders panel
    And a service request is placed with code "12-Lead Electrocardiogram" category "procedure" authored 5 days ago
    And a service request is placed with code "Cardiac Stress Test" category "procedure" authored 5 days ago
    And a service request is placed with code "Transthoracic Echocardiogram" category "imaging" authored 5 days ago

    # Conditions — ordered so care plan addresses the primary diagnosis (last = hypertensive crisis)
    And a condition is recorded with code "Z95.818" display "Cardiac implant device (arc reactor)" status "active" onset 730 days ago
    And a condition is recorded with code "R00.0" display "Tachycardia, unspecified" status "active" onset 90 days ago
    And a condition is recorded with code "R09.02" display "Hypoxemia" status "active" onset 30 days ago
    And a condition is recorded with code "I16.9" display "Hypertensive crisis" status "active" onset 90 days ago

    # Medications — 4 agents reflecting escalating management
    And a medication is recorded with name "Amlodipine 10mg" status "active" dosage "Once daily in the morning" started 90 days ago
    And a medication is recorded with name "Aspirin 81mg" status "active" dosage "Once daily" started 90 days ago
    And a medication is recorded with name "Carvedilol 25mg" status "active" dosage "Twice daily with meals" started 60 days ago
    And a medication is recorded with name "Hydralazine 50mg" status "active" dosage "Three times daily as needed" started 30 days ago

    And an appointment is scheduled 2 days from now with description "Emergency cardiology consultation"

    And a goal is created with description "Reduce systolic BP below 140 mmHg" target LOINC "8480-6" value 140.0 unit "mm[Hg]" due 60 days from now
    And a care plan is created with title "Hypertensive Crisis Management" addressing the last condition with the last goal
