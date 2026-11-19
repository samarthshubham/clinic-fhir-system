Feature: Seed staff practitioners

  The Practitioners behind the dev staff logins (clinic-api application-dev.yaml), as
  StaffAccountBootstrap writes them at startup: id user-<username> and the username
  identifier. Seeding them too means a provider HAPI reset gives the logins their
  practitioner_id back without a clinic-api restart.

  Scenario: Seed the staff practitioners
    Given staff practitioner "admin" "Alex" "Morgan" with title "Clinic administrator" is provisioned
    And staff practitioner "clinician" "Anika" "Patel" with title "Internal Medicine" is provisioned
    And staff practitioner "frontdesk" "Jordan" "Lee" with title "Front desk" is provisioned
