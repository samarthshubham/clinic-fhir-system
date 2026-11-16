package io.github.samarthshubham.clinic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestClient;

class CrdClientContractTest {

  private static final Path OUTPUT_DIR = Path.of("target", "cds-out");

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final CrdClient client =
      new CrdClient(
          mock(RestClient.class),
          mock(PayerTokenClient.class),
          FhirContext.forR4Cached(),
          objectMapper);

  static Stream<Arguments> goldenRequests() {
    return Stream.of(
        Arguments.of(
            "order-sign-request.json",
            "draft-mri-lumbar-spine",
            (Supplier<ServiceRequest>) CrdFixtures::mriOrder),
        Arguments.of(
            "order-sign-request-covered.json",
            "draft-cbc",
            (Supplier<ServiceRequest>) CrdFixtures::cbcOrder));
  }

  @ParameterizedTest
  @MethodSource("goldenRequests")
  void orderSignRequestEqualsTheGoldenFile(
      String fixture, String draftId, Supplier<ServiceRequest> mappedOrder) throws IOException {
    JsonNode golden = CrdFixtures.json(fixture);
    ServiceRequest draft = mappedOrder.get();
    draft.setId(draftId);

    JsonNode request =
        client.buildRequest(
            CrdClient.Hook.ORDER_SIGN,
            golden.path("hookInstance").asText(),
            draft,
            CrdFixtures.patient(golden),
            CrdFixtures.coverage(golden),
            CrdFixtures.practitioner(golden));

    Files.createDirectories(OUTPUT_DIR);
    Files.writeString(
        OUTPUT_DIR.resolve(fixture),
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request) + "\n");
    assertThat(request).isEqualTo(golden);
  }
}
