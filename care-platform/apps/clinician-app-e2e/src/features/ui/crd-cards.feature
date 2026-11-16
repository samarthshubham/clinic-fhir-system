@ui
Feature: CRD coverage cards on orders

  Scenario: MRI order shows PA needed
    Given a Praxis Gold PPO member "mri-member"
    And a test patient "mri"
    And the test patient has Praxis Gold PPO coverage as member "mri-member"
    And I sign in to the clinician app as "clinician"
    When I open the test patient's chart
    And I place the order "MRI lumbar spine without contrast" with CPT code "72148" in category "Imaging"
    Then the order dialog shows the coverage badge "PA needed"
    And the order dialog shows the card "Prior authorization needed: MRI lumbar spine without contrast"
    And the order dialog offers "Complete documentation"
    When I close the order dialog
    Then the pending order "MRI lumbar spine without contrast" shows the coverage badge "PA needed" and the card "Prior authorization needed: MRI lumbar spine without contrast"
