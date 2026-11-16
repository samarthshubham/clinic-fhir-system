package io.github.samarthshubham.clinic.client;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.samarthshubham.clinic.dto.ServiceRequestRequest;
import io.github.samarthshubham.clinic.mapper.ServiceRequestMapper;
import io.github.samarthshubham.clinic.support.GoldenFhir;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;

final class CrdFixtures {

  static final String US_CORE_COVERAGE =
      "http://hl7.org/fhir/us/core/StructureDefinition/us-core-coverage";
  static final LocalDate AUTHORED = LocalDate.of(2026, 11, 11);

  private static final ObjectMapper JSON = new ObjectMapper();

  private CrdFixtures() {}

  static String read(String name) {
    return GoldenFhir.read("crd/" + name);
  }

  static JsonNode json(String name) {
    try {
      return JSON.readTree(read(name));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Patient patient(JsonNode hookRequest) {
    return stored(Patient.class, hookRequest.path("prefetch").path("patient"));
  }

  static Practitioner practitioner(JsonNode hookRequest) {
    return stored(Practitioner.class, hookRequest.path("prefetch").path("practitioner"));
  }

  /** The prefetched Coverage as clinic-api stores it: US Core profile, HAPI version metadata. */
  static Coverage coverage(JsonNode hookRequest) {
    Coverage coverage =
        stored(
            Coverage.class,
            hookRequest.path("prefetch").path("coverage").path("entry").path(0).path("resource"));
    coverage.getMeta().getProfile().clear();
    coverage.getMeta().addProfile(US_CORE_COVERAGE);
    return coverage;
  }

  /**
   * The golden draft Appointment as clinic-api holds it before the hook: booked, no CRD profile.
   */
  static Appointment bookedAppointment(JsonNode hookRequest) {
    JsonNode draft =
        hookRequest.path("context").path("appointments").path("entry").path(0).path("resource");
    Appointment appointment =
        FhirContext.forR4Cached()
            .newJsonParser()
            .parseResource(Appointment.class, draft.toString());
    appointment.setMeta(new Meta());
    appointment.setStatus(Appointment.AppointmentStatus.BOOKED);
    return appointment;
  }

  static ServiceRequest mriOrder() {
    return new ServiceRequestMapper()
        .toFhir(
            new ServiceRequestRequest(
                "seed-sandra-okafor",
                null,
                "seed-default-practitioner",
                "MRI lumbar spine without contrast",
                "http://www.ama-assn.org/go/cpt",
                "72148",
                "imaging",
                "active",
                "routine",
                AUTHORED));
  }

  static ServiceRequest cbcOrder() {
    return new ServiceRequestMapper()
        .toFhir(
            new ServiceRequestRequest(
                "seed-priya-nair",
                null,
                "seed-default-practitioner",
                "Complete Blood Count",
                "http://loinc.org",
                "58410-2",
                "laboratory",
                "active",
                "routine",
                AUTHORED));
  }

  private static <T extends Resource> T stored(Class<T> type, JsonNode json) {
    T resource = FhirContext.forR4Cached().newJsonParser().parseResource(type, json.toString());
    resource.setId(
        "http://localhost:8080/fhir/"
            + resource.fhirType()
            + "/"
            + resource.getIdPart()
            + "/_history/3");
    resource.getMeta().setVersionId("3").setSource("#hapi");
    return resource;
  }
}
