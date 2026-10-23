@api
Feature: Vitals

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"

  Scenario: Record a full panel of vitals
    When I send POST "/api/vitals" with body:
      """
      {"patientId":"{patient}","effectiveDate":"{daysAgo:0}","systolicBp":120,"diastolicBp":80,"weightKg":70.5,"spo2Percent":98,"heartRateBpm":72,"temperatureCelsius":36.8}
      """
    Then the response status is 201
    And the response has 6 items
    And every item in "" has "patientId" equal to "{patient}"
    And some item in "" has "loincCode" equal to "8480-6"
    And some item in "" has "loincCode" equal to "8462-4"
    And some item in "" has "loincCode" equal to "29463-7"
    And some item in "" has "loincCode" equal to "59408-5"
    And some item in "" has "loincCode" equal to "8867-4"
    And some item in "" has "loincCode" equal to "8310-5"

  Scenario: List vitals by patient
    Given the test patient has these vitals:
      | daysAgo | systolic | diastolic | weight |
      | 2       | 118      | 78        | 70     |
      | 1       | 120      | 80        | 70.2   |
    When I send GET "/api/vitals?patientId={patient}"
    Then the response status is 200
    And the response has 6 items

  Scenario: Filter vitals by type
    Given the test patient has these vitals:
      | daysAgo | systolic | diastolic | weight |
      | 2       | 118      | 78        | 70     |
      | 1       | 120      | 80        | 70.2   |
    When I send GET "/api/vitals?patientId={patient}&type=weight"
    Then the response status is 200
    And the response has 2 items
    And every item in "items" has "loincCode" equal to "29463-7"

  Scenario: Recording vitals without a patient id is rejected
    When I send POST "/api/vitals" with body:
      """
      {"effectiveDate":"{daysAgo:0}","systolicBp":120}
      """
    Then the response status is 400

  Scenario: Recording vitals without an effective date is rejected
    When I send POST "/api/vitals" with body:
      """
      {"patientId":"{patient}","systolicBp":120}
      """
    Then the response status is 400

  Scenario: Listing vitals without a patient id is rejected
    When I send GET "/api/vitals"
    Then the response status is 400
