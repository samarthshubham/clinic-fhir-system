@ui
Feature: Clinician prior authorization

  Scenario: Submit a stress test and receive the payer's approval
    Given the payer can validate a PAS request for a cardiac stress test
    And a Praxis Gold PPO member "pas-member"
    And a test patient "pas"
    And the test patient has Praxis Gold PPO coverage as member "pas-member"
    And I sign in to the clinician app as "clinician"
    When I open the test patient's chart
    And I place the order "Cardiac stress test" with CPT code "93015" in category "Procedure"
    And I close the order dialog
    And I submit prior authorization for "Cardiac stress test" in place of service "11"
    Then the order "Cardiac stress test" shows prior authorization "Pended"
    When the payer approves the test patient's cardiac stress test
    Then the order "Cardiac stress test" shows prior authorization "Approved" and its authorization number
    When I open the clinician Inbox for the prior authorization decision
    Then the test patient's Inbox entry says "Prior authorization approved"
    And the clinician app logged no errors during prior authorization
