package io.github.samarthshubham.clinic.mapper;

import static io.github.samarthshubham.clinic.support.FhirDates.localDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.clinic.dto.MedicationRequest;
import io.github.samarthshubham.clinic.dto.MedicationResponse;
import java.time.LocalDate;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.junit.jupiter.api.Test;

class MedicationMapperTest {

  private static final LocalDate START = LocalDate.of(2026, 1, 10);

  private final MedicationMapper mapper = new MedicationMapper();

  @Test
  void mapsMedicationStatusDosageAndStart() {
    MedicationStatement statement =
        mapper.toFhir(
            new MedicationRequest("p1", "Lisinopril 10 mg", "active", "Once daily", START));

    assertThat(statement.getSubject().getReference()).isEqualTo("Patient/p1");
    assertThat(statement.getMedicationCodeableConcept().getText()).isEqualTo("Lisinopril 10 mg");
    assertThat(statement.getStatus())
        .isEqualTo(MedicationStatement.MedicationStatementStatus.ACTIVE);
    assertThat(statement.getDosageFirstRep().getText()).isEqualTo("Once daily");
    assertThat(localDate(statement.getEffectivePeriod().getStart())).isEqualTo(START);
  }

  @Test
  void omitsDosageWhenNotGiven() {
    MedicationStatement statement =
        mapper.toFhir(new MedicationRequest("p1", "Aspirin", "completed", null, START));

    assertThat(statement.getDosage()).isEmpty();
  }

  @Test
  void unknownStatusIsRejected() {
    MedicationRequest request = new MedicationRequest("p1", "Aspirin", "taking", null, START);

    assertThatThrownBy(() -> mapper.toFhir(request)).isInstanceOf(FHIRException.class);
  }

  @Test
  void toResponseIsTheReverseOfToFhir() {
    MedicationStatement statement =
        mapper.toFhir(new MedicationRequest("p1", "Lisinopril", "active", "Once daily", START));
    statement.setId("m1");

    assertThat(mapper.toResponse(statement))
        .isEqualTo(new MedicationResponse("m1", "p1", "Lisinopril", "active", "Once daily", START));
  }

  @Test
  void toResponseLeavesMissingFieldsNull() {
    MedicationStatement statement = new MedicationStatement();
    statement.setId("m1");

    assertThat(mapper.toResponse(statement))
        .isEqualTo(new MedicationResponse("m1", null, null, null, null, null));
  }
}
