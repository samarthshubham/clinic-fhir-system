package io.github.samarthshubham.payer.cdshooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.samarthshubham.payer.config.CrdProperties;
import java.util.List;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PrefetchResolverTest {

  private static final String ALLOWED = "http://localhost:1/fhir";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final IParser parser = FhirContext.forR4Cached().newJsonParser();
  private final PrefetchResolver resolver =
      new PrefetchResolver(
          FhirContext.forR4Cached(),
          new CrdProperties("Praxis Health Plan", List.of(), List.of(ALLOWED + "/")));

  @Test
  void prefetchedResourcesAreUsedAsSent() throws Exception {
    CdsHookRequest request =
        request(
            "{\"context\":{\"patientId\":\"p1\"},\"prefetch\":{\"patient\":"
                + "{\"resourceType\":\"Patient\",\"id\":\"p1\"}}}");

    assertThat(resolver.resolve(request, CrdHook.PATIENT_PREFETCH, parser))
        .isInstanceOfSatisfying(
            Patient.class, patient -> assertThat(patient.getIdPart()).isEqualTo("p1"));
  }

  @Test
  void prefetchThatIsNotFhirIs400() throws Exception {
    CdsHookRequest request =
        request("{\"context\":{},\"prefetch\":{\"patient\":{\"resourceType\":\"Nope\"}}}");

    assertStatus(() -> resolver.resolve(request, CrdHook.PATIENT_PREFETCH, parser), 400);
  }

  @Test
  void missingPrefetchWithoutAnAllowedServerIs412() throws Exception {
    CdsHookRequest noServer = request("{\"context\":{\"patientId\":\"p1\"}}");
    CdsHookRequest otherServer =
        request("{\"fhirServer\":\"http://evil.example/fhir\",\"context\":{\"patientId\":\"p1\"}}");

    assertStatus(() -> resolver.resolve(noServer, CrdHook.COVERAGE_PREFETCH, parser), 412);
    assertStatus(() -> resolver.resolve(otherServer, CrdHook.COVERAGE_PREFETCH, parser), 412);
  }

  @Test
  void anAllowedServerThatCannotAnswerIs412() throws Exception {
    CdsHookRequest request =
        request(
            "{\"fhirServer\":\""
                + ALLOWED
                + "\",\"prefetch\":{\"patient\":null},"
                + "\"context\":{\"patientId\":\"p1\"}}");

    assertStatus(() -> resolver.resolve(request, CrdHook.PATIENT_PREFETCH, parser), 412);
  }

  @Test
  void unknownPrefetchKeyIsAProgrammingError() throws Exception {
    CdsHookRequest request = request("{\"context\":{}}");

    assertThatThrownBy(() -> resolver.resolve(request, "encounter", parser))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void templatesAreFilledFromTheContext() throws Exception {
    CdsHookRequest request =
        request("{\"context\":{\"patientId\":\"p-1.a\",\"userId\":\"Practitioner/u1\"}}");

    assertThat(PrefetchResolver.render(CrdHook.PREFETCH.get("coverage"), request))
        .isEqualTo("Coverage?patient=p-1.a&status=active");
    assertThat(PrefetchResolver.render(CrdHook.PREFETCH.get("practitioner"), request))
        .isEqualTo("Practitioner/u1");
  }

  @Test
  void contextValuesThatAreNotIdsAreRejected() throws Exception {
    CdsHookRequest injection =
        request("{\"context\":{\"patientId\":\"p1&_include=*\",\"userId\":\"../admin\"}}");
    CdsHookRequest missing = request("{\"context\":{}}");

    assertStatus(() -> PrefetchResolver.render("Patient/{{context.patientId}}", injection), 400);
    assertStatus(() -> PrefetchResolver.render("{{context.userId}}", injection), 400);
    assertStatus(() -> PrefetchResolver.render("Patient/{{context.patientId}}", missing), 400);
  }

  private CdsHookRequest request(String json) throws Exception {
    return CdsHookRequest.from(objectMapper.readTree(json));
  }

  private static void assertStatus(Runnable call, int status) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            CdsHookException.class,
            e -> assertThat(e.status()).isEqualTo(HttpStatus.valueOf(status)));
  }
}
