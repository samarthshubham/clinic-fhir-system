Feature: Seed Patient — Robert Osei (acute hypertension, critical alerts)

  72-year-old male presenting with hypertensive crisis and hypoxia.
  Demonstrates: critical BP alert (systolic 185 > 180 threshold), SpO₂ warning
  (91% < 95%), STAT pending order, 2 active conditions, no care plan yet.

  Background: Infrastructure provisioned
    Given practitioner "seed-default-practitioner" is provisioned
    And organization "seed-default-org" is provisioned

  Scenario: Seed Robert Osei
    Given a patient exists with the following details:
      | id          | seed-robert-osei        |
      | firstName   | Robert                  |
      | lastName    | Osei                    |
      | dateOfBirth | 1953-08-22              |
      | gender      | male                    |
      | phone       | 5551007007              |
      | email       | robert.osei@example.com |
      | mrn         | PX-10000007             |

    And the patient is covered by Praxis Health Plan:
      | memberId       | PHP-10007       |
      | planName       | Praxis Gold PPO |
      | status         | active          |
      | startedDaysAgo | 365             |

    # Encounter 1 — ER presentation, critical vitals
    When an encounter is recorded 10 days ago with reason "Emergency: chest tightness and severe hypertension" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 185 |
      | diastolicBp  | 115 |
      | weightKg     | 87  |
      | spo2Percent  | 91  |
      | heartRateBpm | 98  |

    # STAT cardiac workup — no report yet (pending orders panel)
    And a service request is placed with code "Emergency Cardiac Workup" category "procedure" priority "stat" authored 10 days ago

    # Encounter 2 — follow-up, partially controlled
    When an encounter is recorded 3 days ago with reason "Hypertension follow-up — BP partially controlled" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 165 |
      | diastolicBp  | 102 |
      | weightKg     | 87  |
      | spo2Percent  | 93  |
      | heartRateBpm | 88  |

    # Clinical resources
    And a condition is recorded with code "I16.9" display "Hypertensive crisis" status "active" onset 15 days ago
    And a condition is recorded with code "J44.1" display "COPD with acute exacerbation" status "active" onset 730 days ago
    And a medication is recorded with name "IV Labetalol" status "stopped" dosage "IV titrated to BP response" started 10 days ago
    And a medication is recorded with name "Amlodipine 10mg" status "active" dosage "Once daily" started 3 days ago
    And an appointment is scheduled 7 days from now with description "Urgent BP and respiratory follow-up"
