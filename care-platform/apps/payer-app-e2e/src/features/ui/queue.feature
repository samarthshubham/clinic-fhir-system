@ui
Feature: Prior authorization review queue

  Scenario: A pended request appears in the queue
    Given a member with an overdue pended cardiac stress test authorization
    When the reviewer signs in to the payer app as "reviewer"
    Then the payer app shows the review queue
    And the queue shows the pended request with its deadline and breach

  Scenario: A reviewer approves an authorization from the review screen
    Given a member with an overdue pended cardiac stress test authorization
    When the reviewer signs in to the payer app as "reviewer"
    And the reviewer opens the pended authorization
    And the reviewer approves the authorization
    Then the approval is saved and removed from the queue

  Scenario: A reviewer cannot deny without a reason
    Given a member with an overdue pended cardiac stress test authorization
    When the reviewer signs in to the payer app as "reviewer"
    And the reviewer opens the pended authorization
    Then denial requires both a reason code and reason text
