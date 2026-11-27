@ui
Feature: Provider directory

  Scenario: A reviewer browses the published provider directory
    Given the payer publishes the Plan-Net directory
    And the reviewer signs in to the payer app as "reviewer"
    And the payer app shows the review queue
    When I open the provider directory
    And I search the directory for "Internal Medicine" in "Springfield"
    Then the directory lists "Aisha Patel" at "400 Example Avenue"
    When I search the directory for "Internal Medicine" in "Chicago"
    Then the directory has no matching providers
