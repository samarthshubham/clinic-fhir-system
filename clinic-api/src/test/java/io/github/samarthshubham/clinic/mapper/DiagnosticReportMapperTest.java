package io.github.samarthshubham.clinic.mapper;

import static io.github.samarthshubham.clinic.support.FhirDates.localDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.clinic.dto.DiagnosticReportRequest;
import io.github.samarthshubham.clinic.dto.DiagnosticReportResponse;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

class DiagnosticReportMapperTest {

  private static final LocalDate ISSUED = LocalDate.of(2026, 3, 5);

  private final DiagnosticReportMapper mapper = new DiagnosticReportMapper();

  @Test
  void mapsReportFieldsReferencesAndResults() {
    DiagnosticReport report =
        mapper.toFhir(
            new DiagnosticReportRequest(
                "p1",
                "e1",
                "sr1",
                "Lipid panel",
                "final",
                ISSUED,
                "LDL elevated",
                List.of("o1", "o2")));

    assertThat(report.getSubject().getReference()).isEqualTo("Patient/p1");
    assertThat(report.getEncounter().getReference()).isEqualTo("Encounter/e1");
    assertThat(report.getBasedOn())
        .extracting(Reference::getReference)
        .containsExactly("ServiceRequest/sr1");
    assertThat(report.getCode().getText()).isEqualTo("Lipid panel");
    assertThat(report.getStatus()).isEqualTo(DiagnosticReport.DiagnosticReportStatus.FINAL);
    assertThat(localDate(report.getIssued())).isEqualTo(ISSUED);
    assertThat(report.getConclusion()).isEqualTo("LDL elevated");
    assertThat(report.getResult())
        .extracting(Reference::getReference)
        .containsExactly("Observation/o1", "Observation/o2");
  }

  @Test
  void unknownStatusIsRejected() {
    DiagnosticReportRequest request =
        new DiagnosticReportRequest("p1", null, null, "X-ray", "done", ISSUED, null, null);

    assertThatThrownBy(() -> mapper.toFhir(request)).isInstanceOf(FHIRException.class);
  }

  @Test
  void omitsOptionalFieldsWhenNotGiven() {
    DiagnosticReport report =
        mapper.toFhir(
            new DiagnosticReportRequest(
                "p1", null, null, "Chest X-ray", "preliminary", ISSUED, null, null));

    assertThat(report.hasEncounter()).isFalse();
    assertThat(report.getBasedOn()).isEmpty();
    assertThat(report.hasConclusion()).isFalse();
    assertThat(report.getResult()).isEmpty();
  }

  @Test
  void toResponseIsTheReverseOfToFhir() {
    DiagnosticReport report =
        mapper.toFhir(
            new DiagnosticReportRequest(
                "p1", "e1", "sr1", "Lipid panel", "final", ISSUED, "Normal", List.of("o1", "o2")));
    report.setId("r1");

    assertThat(mapper.toResponse(report))
        .isEqualTo(
            new DiagnosticReportResponse(
                "r1",
                "p1",
                "e1",
                "sr1",
                "Lipid panel",
                "final",
                ISSUED,
                "Normal",
                List.of("o1", "o2")));
  }

  @Test
  void toResponseLeavesMissingFieldsNullAndResultsEmpty() {
    DiagnosticReport report = new DiagnosticReport();
    report.setId("r1");

    assertThat(mapper.toResponse(report))
        .isEqualTo(
            new DiagnosticReportResponse(
                "r1", null, null, null, null, null, null, null, List.of()));
  }
}
