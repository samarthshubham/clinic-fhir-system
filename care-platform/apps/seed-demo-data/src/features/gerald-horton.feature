Feature: Seed Patient — Gerald Horton (sleep apnea, improving SpO2)

  49-year-old male with moderate OSA showing clear SpO₂ improvement arc on CPAP.
  Demonstrates: critical SpO₂ alert (88% at initial visit), 5-encounter timeline,
  improving trend (88% → 97%), off-track CPAP compliance goal, 1 pending SR.

  Background: Infrastructure provisioned
    Given practitioner "seed-default-practitioner" is provisioned
    And organization "seed-default-org" is provisioned

  Scenario: Seed Gerald Horton
    Given a patient exists with the following details:
      | id          | seed-gerald-horton        |
      | firstName   | Gerald                    |
      | lastName    | Horton                    |
      | dateOfBirth | 1974-11-04                |
      | gender      | male                      |
      | phone       | 5551003003                |
      | email       | gerald.horton@example.com |
      | mrn         | PX-10000003               |

    And the patient is covered by Praxis Health Plan:
      | memberId       | PHP-10003       |
      | planName       | Praxis Gold PPO |
      | status         | active          |
      | startedDaysAgo | 365             |

    # Sleep study ordered at first visit
    And a service request is placed with code "Polysomnography" category "procedure" authored 75 days ago

    # Encounter 1 — initial presentation, critical SpO₂ (88% < 90%)
    When an encounter is recorded 75 days ago with reason "Fatigue, snoring and shortness of breath — initial evaluation" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 148 |
      | diastolicBp  | 96  |
      | weightKg     | 98  |
      | spo2Percent  | 88  |
      | heartRateBpm | 84  |

    # Sleep study report — 10 days after study
    And a diagnostic report is created from the last service request with title "Polysomnography — Sleep Study" issued 65 days ago
    And the last diagnostic report conclusion is "AHI 28 events/hour. Moderate-severe obstructive sleep apnea. CPAP therapy initiated at 9 cmH2O. Oxygen supplementation considered."

    # Encounter 2 — early CPAP, SpO₂ warning (91%)
    When an encounter is recorded 55 days ago with reason "Sleep study result and CPAP initiation" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 146 |
      | diastolicBp  | 94  |
      | weightKg     | 97  |
      | spo2Percent  | 91  |
      | heartRateBpm | 82  |

    # Encounter 3 — early improvement
    When an encounter is recorded 40 days ago with reason "CPAP titration — early response" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 142 |
      | diastolicBp  | 91  |
      | weightKg     | 97  |
      | spo2Percent  | 94  |
      | heartRateBpm | 80  |

    # Encounter 4 — sustained improvement
    When an encounter is recorded 25 days ago with reason "CPAP adherence check — sustained improvement" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 138 |
      | diastolicBp  | 89  |
      | weightKg     | 96  |
      | spo2Percent  | 96  |
      | heartRateBpm | 78  |

    # Encounter 5 — most recent, good control
    When an encounter is recorded 5 days ago with reason "Sleep quality improved — CPAP adherent" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 132 |
      | diastolicBp  | 85  |
      | weightKg     | 95  |
      | spo2Percent  | 97  |
      | heartRateBpm | 76  |

    # Pending overnight oximetry — active, no report (demonstrates pending order)
    And a service request is placed with code "Overnight Pulse Oximetry" category "procedure" authored 5 days ago

    # Clinical resources
    And a condition is recorded with code "78275009" display "Obstructive sleep apnea" status "active" onset 480 days ago
    And a medication is recorded with name "CPAP therapy" status "active" dosage "Nightly CPAP at 9 cmH2O" started 65 days ago
    And an appointment is scheduled 14 days from now with description "CPAP compliance and SpO2 review"

    # Goal 1 — SpO₂ target on track (latest 97% > 95%)
    And a goal is created with description "Maintain SpO2 above 95% consistently" target LOINC "59408-5" value 95.0 unit "%" due 90 days from now
    # Goal 2 — CPAP compliance, text-only goal (no observable LOINC measure)
    And a goal is created with description "Consistent CPAP use 6 hours per night" due 60 days from now
    And a care plan is created with title "Sleep Apnea Management" addressing the last condition with all goals
