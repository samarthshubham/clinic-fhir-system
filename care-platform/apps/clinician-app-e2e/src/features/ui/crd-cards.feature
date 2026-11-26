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

  Scenario: MRI order from the patient header shows PA needed
    Given a Praxis Gold PPO member "mri-header-member"
    And a test patient "mri-header"
    And the test patient has Praxis Gold PPO coverage as member "mri-header-member"
    And I sign in to the clinician app as "clinician"
    When I open the test patient's chart
    And I place the order "MRI lumbar spine without contrast" with CPT code "72148" in category "Imaging" from the patient header
    Then the order dialog shows the coverage badge "PA needed"
    And the order dialog shows the card "Prior authorization needed: MRI lumbar spine without contrast"
    When I close the order dialog
    Then the pending order "MRI lumbar spine without contrast" shows the coverage badge "PA needed" and the card "Prior authorization needed: MRI lumbar spine without contrast"

  Scenario: Complete MRI documentation
    Given a Praxis Gold PPO member "mri-dtr-member"
    And a test patient "mri-dtr"
    And the test patient has Praxis Gold PPO coverage as member "mri-dtr-member"
    And the payer offers the MRI documentation questionnaire
    And the test patient has a chart diagnosis of low back pain
    And I sign in to the clinician app as "clinician"
    When I open the test patient's chart
    And I place the order "MRI lumbar spine without contrast" with CPT code "72148" in category "Imaging"
    And I open documentation from the order dialog
    Then the MRI form shows the diagnosis prefilled from the chart
    When I enter 6 weeks of conservative therapy and save the draft
    And I close documentation
    And I close the order dialog
    And I reopen documentation from the pending order "MRI lumbar spine without contrast"
    Then the MRI form shows 6 weeks of conservative therapy
    When I record a neurological deficit and no prior imaging
    And I try to complete the MRI documentation
    Then the deficit description is required
    When I describe the deficit and complete the MRI documentation
    Then the MRI documentation is complete
    When I close documentation
    And I reopen documentation from the pending order "MRI lumbar spine without contrast"
    Then the MRI documentation is complete
    And the clinician app logged no errors during documentation
