Feature: Seed Patient — Sandra Okafor (post-cholecystectomy recovery, lumbar radiculopathy)

  44-year-old female recovering from laparoscopic cholecystectomy.
  Demonstrates: pre-op → post-op vital normalisation over 4 visits, surgical pathology
  order → report cycle, resolved condition, stopped medication, 2 achieved goals,
  completed care plan.
  Back pain chart for DTR prepopulation of the MRI lumbar spine form: active lumbar
  radiculopathy for 42 days, naproxen, a lumbar spine X-ray and 4 physical therapy
  visits, so 4 weeks of conservative therapy (the payer's MRI rule asks for 6).

  Background: Infrastructure provisioned
    Given practitioner "seed-default-practitioner" is provisioned
    And organization "seed-default-org" is provisioned

  Scenario: Seed Sandra Okafor
    Given a patient exists with the following details:
      | id          | seed-sandra-okafor        |
      | firstName   | Sandra                    |
      | lastName    | Okafor                    |
      | dateOfBirth | 1980-02-19                |
      | gender      | female                    |
      | phone       | 5551004004                |
      | email       | sandra.okafor@example.com |
      | mrn         | PX-10000005               |

    # Pathology ordered at pre-op visit
    And a service request is placed with code "Surgical Pathology — Gallbladder" category "laboratory" authored 44 days ago

    # Encounter 1 — pre-op assessment
    When an encounter is recorded 44 days ago with reason "Cholecystectomy — pre-op assessment" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 118 |
      | diastolicBp  | 76  |
      | weightKg     | 72  |
      | spo2Percent  | 98  |
      | heartRateBpm | 72  |

    # Pathology report arrives 4 days later
    And a diagnostic report is created from the last service request with title "Surgical Pathology — Gallbladder" issued 40 days ago
    And the last diagnostic report conclusion is "Cholelithiasis confirmed. Multiple mixed cholesterol stones. Specimen otherwise unremarkable."

    # Encounter 2 — post-op day 1
    When an encounter is recorded 35 days ago with reason "Cholecystectomy post-op day 1" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 122 |
      | diastolicBp  | 78  |
      | weightKg     | 71  |
      | spo2Percent  | 97  |
      | heartRateBpm | 80  |

    # Encounter 3 — wound check
    When an encounter is recorded 21 days ago with reason "Post-op follow-up — wound check" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 116 |
      | diastolicBp  | 74  |
      | weightKg     | 70  |
      | spo2Percent  | 98  |
      | heartRateBpm | 74  |

    # Encounter 4 — final clearance
    When an encounter is recorded 7 days ago with reason "Post-op 4-week clearance — full recovery confirmed" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 114 |
      | diastolicBp  | 73  |
      | weightKg     | 69  |
      | spo2Percent  | 99  |
      | heartRateBpm | 72  |

    # Clinical resources — resolved condition, stopped medication, completed care plan
    And a condition is recorded with code "271807003" display "Cholelithiasis" status "resolved" onset 60 days ago
    And a medication is recorded with name "Ibuprofen 400mg" status "stopped" dosage "Three times daily with food" started 35 days ago
    And an appointment is scheduled 7 days from now with description "Final post-surgical clearance"

    # Both goals achieved — demonstrates completed workflow
    And an achieved goal is created with description "Return to pre-op body weight" target LOINC "29463-7" value 72.0 unit "kg"
    And an achieved goal is created with description "Pain-free ambulation" target LOINC "55423-8" value 7000.0 unit "steps/day"
    And a completed care plan is created with title "Post-Surgical Recovery" addressing all conditions with all goals

    # Back pain — lumbar radiculopathy with 4 weeks of conservative therapy
    When an encounter is recorded 34 days ago with reason "Low back pain radiating to the left leg" and status "finished"
    And a condition is recorded with code "128196005" display "Lumbar radiculopathy" status "active" onset 42 days ago
    And a medication is recorded with name "Naproxen 500mg" status "active" dosage "Twice daily with food" started 34 days ago
    And a service request is placed with code "X-ray lumbar spine, 2 views" category "imaging" authored 34 days ago
    And a diagnostic report is created from the last service request with title "X-ray lumbar spine, 2 views" issued 33 days ago
    And the last diagnostic report conclusion is "Mild L4-L5 disc space narrowing. No fracture or spondylolisthesis."
    When an encounter is recorded 30 days ago with reason "Physical therapy — lumbar spine" and status "finished"
    When an encounter is recorded 23 days ago with reason "Physical therapy — lumbar spine" and status "finished"
    When an encounter is recorded 16 days ago with reason "Physical therapy — lumbar spine" and status "finished"
    When an encounter is recorded 6 days ago with reason "Physical therapy — lumbar spine" and status "finished"
