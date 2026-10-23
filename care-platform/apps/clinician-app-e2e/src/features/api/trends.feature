@api
Feature: Trends

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"
    And the test patient has these vitals:
      | daysAgo | systolic | diastolic | weight | spo2 |
      | 300     | 140      | 90        | 80     | 97   |
      | 60      | 138      | 88        | 79     | 96   |
      | 20      | 135      | 85        | 78     | 95   |
      | 3       | 130      | 82        | 77     | 98   |

  Scenario: Trends default to 30 days and blood pressure
    When I send GET "/api/patients/{patient}/trends"
    Then the response status is 200
    And the response field "period" is "30d"
    And the response field "series.bp.systolic" has 2 items
    And the response field "series.bp.diastolic" has 2 items

  Scenario Outline: The period limits how far back the series goes
    When I send GET "/api/patients/{patient}/trends?type=bp&period=<period>"
    Then the response field "period" is "<period>"
    And the response field "series.bp.systolic" has <count> items

    Examples:
      | period | count |
      | 7d     | 1     |
      | 30d    | 2     |
      | 90d    | 3     |
      | 1y     | 4     |

  Scenario: The type filter selects the series
    When I send GET "/api/patients/{patient}/trends?type=weight,spo2&period=90d"
    Then the response status is 200
    And the response field "series.weight.values" has 3 items
    And the response field "series.spo2.values" has 3 items
    And the response field "series.bp" is null
