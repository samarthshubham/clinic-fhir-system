@ui
Feature: Find a member

  The scenario creates its own member in the payer HAPI, so it never depends
  on the demo seed. Cleanup removes the member and its coverage.

  Scenario: A reviewer finds a member by name and member ID and opens their coverage
    Given a Praxis Gold PPO member "member"
    When the reviewer signs in to the payer app as "reviewer"
    And the reviewer opens the members page
    And the reviewer searches members by the name of "member"
    Then the members table lists only "member"
    When the reviewer searches members by the member ID of "member"
    Then the members table lists only "member"
    When the reviewer opens the member "member"
    Then the member page shows the Praxis Gold PPO coverage of "member"
    And the member has no prior authorizations
