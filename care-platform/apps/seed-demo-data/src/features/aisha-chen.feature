Feature: Seed Patient — Aisha Chen (new patient, minimal data)

  28-year-old female, first clinic visit. Demonstrates: empty conditions,
  empty alerts, empty medications after discharge, minimal 1-event timeline,
  no care plan, no pending orders — pure empty-state showcase.

  Background: Infrastructure provisioned
    Given practitioner "seed-default-practitioner" is provisioned
    And organization "seed-default-org" is provisioned

  Scenario: Seed Aisha Chen
    Given a patient exists with the following details:
      | id          | seed-aisha-chen        |
      | firstName   | Aisha                  |
      | lastName    | Chen                   |
      | dateOfBirth | 1997-04-15             |
      | gender      | female                 |
      | phone       | 5551006006             |
      | email       | aisha.chen@example.com |
      | mrn         | PX-10000008            |

    And the patient is covered by Praxis Health Plan:
      | memberId       | PHP-10008       |
      | planName       | Praxis Gold PPO |
      | status         | cancelled       |
      | startedDaysAgo | 400             |
      | endedDaysAgo   | 30              |

    # Single encounter — new patient initial assessment, all vitals normal
    When an encounter is recorded 5 days ago with reason "New patient — initial assessment" and status "finished"
    And vitals are recorded for that encounter:
      | systolicBp   | 114 |
      | diastolicBp  | 72  |
      | weightKg     | 62  |
      | spo2Percent  | 99  |
      | heartRateBpm | 68  |

    And an appointment is scheduled 21 days from now with description "Preventive care follow-up"
