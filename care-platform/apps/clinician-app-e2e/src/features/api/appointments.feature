@api
Feature: Appointments

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"

  Scenario: Schedule an appointment
    When I send POST "/api/appointments" with body:
      """
      {"patientId":"{patient}","start":"{daysAhead:3}T09:00:00","end":"{daysAhead:3}T09:30:00","description":"Follow-up visit","status":"booked"}
      """
    Then the response status is 201
    And the response field "description" is "Follow-up visit"
    And the response field "status" is "booked"

  Scenario: Schedule an appointment with a practitioner
    Given "admin" creates "practitioner" with POST "/api/practitioners":
      """
      {"firstName":"Dana","lastName":"E2E-{runId}","specialty":"Cardiology"}
      """
    When I send POST "/api/appointments" with body:
      """
      {"patientId":"{patient}","practitionerId":"{practitioner}","start":"{daysAhead:4}T10:00:00","end":"{daysAhead:4}T10:30:00","status":"booked"}
      """
    Then the response status is 201
    And the response field "practitionerId" is "{practitioner}"

  Scenario: List appointments by patient
    Given "clinician" creates "appointment" with POST "/api/appointments":
      """
      {"patientId":"{patient}","start":"{daysAhead:2}T09:00:00","end":"{daysAhead:2}T09:30:00","description":"Check-in","status":"booked"}
      """
    When I send GET "/api/appointments?patientId={patient}"
    Then the response status is 200
    And the response has 1 items

  Scenario: Appointment without a start time is rejected
    When I send POST "/api/appointments" with body:
      """
      {"patientId":"{patient}","end":"{daysAhead:2}T09:30:00","status":"booked"}
      """
    Then the response status is 400
