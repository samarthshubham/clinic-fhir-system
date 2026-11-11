@ui
Feature: Patient coverage

  Scenario: add coverage
    Given a test patient "coverage"
    And I sign in to the clinician app as "clinician"
    When I open the test patient's chart
    Then the chart shows no active coverage
    When I add coverage with payer "Praxis Health Plan" ("praxis-health-plan"), plan "Praxis Gold PPO" and member id "PHP-60001"
    Then the chart shows active coverage from "Praxis Health Plan" on plan "Praxis Gold PPO" with member id "PHP-60001"
    And focus is back on the coverage edit button
