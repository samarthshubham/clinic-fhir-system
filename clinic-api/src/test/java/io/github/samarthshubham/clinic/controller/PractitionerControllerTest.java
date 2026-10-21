package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.dto.PractitionerRequest;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.service.PractitionerService;
import org.hl7.fhir.r4.model.Practitioner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(PractitionerController.class)
class PractitionerControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private PractitionerService practitionerService;

  @Test
  void createReturnsCreatedWithFhirPractitioner() {
    Practitioner created = new Practitioner();
    created.setId("pr1");
    when(practitionerService.createPractitioner(any())).thenReturn(created);

    assertThat(
            mvc.post()
                .uri("/api/practitioners")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"firstName":"Anil","lastName":"Rao","specialty":"Cardiology",
                     "npi":"1234567890","email":"anil@example.com"}
                    """))
        .hasStatus(HttpStatus.CREATED)
        .hasContentType("application/fhir+json")
        .bodyJson()
        .extractingPath("$.id")
        .isEqualTo("pr1");
    verify(practitionerService)
        .createPractitioner(
            new PractitionerRequest("Anil", "Rao", "Cardiology", "1234567890", "anil@example.com"));
  }

  @Test
  void createWithoutSpecialtyReturnsBadRequest() {
    assertThat(
            mvc.post()
                .uri("/api/practitioners")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"firstName":"Anil","lastName":"Rao"}
                    """))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("must not be blank");
    verifyNoInteractions(practitionerService);
  }

  @Test
  void getReturnsPractitionerById() {
    Practitioner practitioner = new Practitioner();
    practitioner.setId("pr1");
    when(practitionerService.getPractitioner("pr1")).thenReturn(practitioner);

    assertThat(mvc.get().uri("/api/practitioners/pr1"))
        .hasStatusOk()
        .hasContentType("application/fhir+json")
        .bodyJson()
        .extractingPath("$.id")
        .isEqualTo("pr1");
  }

  @Test
  void getUnknownPractitionerReturnsNotFound() {
    when(practitionerService.getPractitioner("missing"))
        .thenThrow(new ResourceNotFoundException("Practitioner", "missing"));

    assertThat(mvc.get().uri("/api/practitioners/missing")).hasStatus(HttpStatus.NOT_FOUND);
  }
}
