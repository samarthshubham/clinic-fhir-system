package io.github.shubhamforge.clinic.config;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class ActuatorEndpointsTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private IGenericClient fhirClient;

  @Test
  void healthReportsUp() {
    assertThat(mvc.get().uri("/actuator/health"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("UP");
  }

  @Test
  void prometheusExposesHttpLatencyHistogram() {
    assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();

    assertThat(mvc.get().uri("/actuator/prometheus"))
        .hasStatusOk()
        .bodyText()
        .contains("http_server_requests_seconds_bucket");
  }

  @Test
  void unlistedEndpointsAreNotExposed() {
    assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.NOT_FOUND);
  }
}
