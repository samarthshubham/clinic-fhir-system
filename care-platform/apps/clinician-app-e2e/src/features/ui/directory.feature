@ui
Feature: Provider directory

  Scenario: A clinician finds in-network providers for a referral
    Given the payer publishes the Plan-Net directory
    And a test patient "referral"
    And I sign in to the clinician app as "clinician"
    And I open the test patient's chart
    And I add coverage with payer "Praxis Health Plan" ("praxis-health-plan"), plan "Praxis Gold PPO" and member id "PHP-60002"
    When I open the provider directory
    And I search the directory for "Internal Medicine" in "Springfield"
    Then the directory lists "Aisha Patel" at "400 Example Avenue"
    When I search the directory for "Internal Medicine" in "Chicago"
    Then the directory has no matching providers
