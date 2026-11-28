Feature: Seed staff practitioners

  The Practitioners behind the dev staff logins (clinic-api application-dev.yaml), as
  StaffAccountBootstrap writes them at startup: id user-<username> and the username
  identifier. Seeding them too means a provider HAPI reset gives the logins their
  practitioner_id back without a clinic-api restart.

  Scenario: Seed the staff practitioners
    Given the following staff practitioners are provisioned:
      | username  | firstName | lastName | title                | npi        |
      | admin     | Alex      | Morgan   | Clinic administrator |            |
      | clinician | Anika     | Patel    | Internal Medicine    | 1234567893 |
      | frontdesk | Jordan    | Lee      | Front desk           |            |
