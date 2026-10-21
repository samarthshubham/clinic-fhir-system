package io.github.samarthshubham.clinic.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import io.github.samarthshubham.clinic.controller.PatientController;
import io.github.samarthshubham.clinic.service.PatientService;
import io.github.samarthshubham.clinic.service.SnapshotService;
import io.github.samarthshubham.clinic.service.TimelineService;
import io.github.samarthshubham.clinic.service.TrendsService;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(PatientController.class)
class GlobalExceptionHandlerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private PatientService patientService;
  @MockitoBean private SnapshotService snapshotService;
  @MockitoBean private TrendsService trendsService;
  @MockitoBean private TimelineService timelineService;

  @Test
  void resourceNotFoundBecomesNotFoundWithMessage() {
    when(patientService.getPatient("missing"))
        .thenThrow(new ResourceNotFoundException("Patient", "missing"));

    assertThat(getPatient("missing"))
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentType(MediaType.APPLICATION_JSON)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":404,"message":"Patient with id 'missing' not found"}
            """);
  }

  @Test
  void referenceValidationBecomesUnprocessableEntity() {
    when(patientService.getPatient("p1"))
        .thenThrow(new ReferenceValidationException("Practitioner", "pr9"));

    assertThat(getPatient("p1"))
        .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":422,"message":"Referenced Practitioner with id 'pr9' does not exist"}
            """);
  }

  @Test
  void unreachableFhirServerBecomesServiceUnavailable() {
    FhirClientConnectionException failure = new FhirClientConnectionException("Connection refused");
    when(patientService.getPatient("p1")).thenThrow(failure);

    assertThat(getPatient("p1"))
        .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
        .bodyJson()
        .isStrictlyEqualTo(
            "{\"status\":503,\"message\":\"FHIR server unavailable: "
                + failure.getMessage()
                + "\"}");
  }

  static Stream<Arguments> fhirServerErrors() {
    return Stream.of(
        Arguments.of(new InternalErrorException("FHIR server error"), 500),
        Arguments.of(new ForbiddenOperationException("Not allowed"), 403),
        Arguments.of(new InvalidRequestException("Bad search parameter"), 400),
        Arguments.of(
            new ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException("Patient/x"), 404));
  }

  @ParameterizedTest
  @MethodSource("fhirServerErrors")
  void fhirServerErrorsAreForwardedWithTheirStatus(
      BaseServerResponseException failure, int expectedStatus) {
    when(patientService.getPatient("p1")).thenThrow(failure);

    assertThat(getPatient("p1"))
        .hasStatus(expectedStatus)
        .bodyJson()
        .isStrictlyEqualTo(
            "{\"status\":" + expectedStatus + ",\"message\":\"" + failure.getMessage() + "\"}");
  }

  @Test
  void validationErrorsAreJoinedIntoOneMessage() {
    MvcTestResult result =
        mvc.post()
            .uri("/api/patients")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"firstName\":\"Priya\",\"lastName\":\"Nair\"}")
            .exchange();

    assertThat(result)
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo(400);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.message")
        .asString()
        .satisfies(
            message ->
                assertThat(List.of(message.split(", ")))
                    .containsExactlyInAnyOrder("must not be null", "must not be blank"));
  }

  private MvcTestResult getPatient(String id) {
    return mvc.get().uri("/api/patients/{id}", id).exchange();
  }
}
