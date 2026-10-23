@ui
Feature: Patient roster

  Scenario: A clinician finds a newly registered patient and opens the chart
    Given a test patient "roster"
    And I sign in to the clinician app as "clinician"
    When I search the roster for the test patient
    Then the roster lists the test patient
    When I open the test patient's chart from the roster
    Then the chart header shows the test patient

  Scenario: Searching for an unknown name shows an empty state
    Given I sign in to the clinician app as "clinician"
    When I search the roster for "zz-nobody-by-this-name"
    Then the roster shows no matches
