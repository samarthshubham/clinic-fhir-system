package io.github.shubhamforge.clinic.service;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static io.github.shubhamforge.clinic.support.FhirClientStub.quantityObservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

  @Mock private PatientService patientService;
  @Mock private EncounterService encounterService;
  @Mock private VitalsService vitalsService;

  private SummaryService service;

  @BeforeEach
  void setUp() {
    service = new SummaryService(patientService, encounterService, vitalsService);
  }

  @Test
  void summaryIsPatientThenEncountersThenVitals() {
    Patient patient = new Patient();
    Encounter visit = new Encounter();
    Observation systolic = quantityObservation(150);
    Observation weight = quantityObservation(80);
    when(patientService.getPatient("p1")).thenReturn(patient);
    when(encounterService.getEncountersForPatient("p1")).thenReturn(bundleOf(visit));
    when(vitalsService.getVitals("p1", null)).thenReturn(bundleOf(systolic, weight));

    Bundle summary = service.getSummary("p1");

    assertThat(summary.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
    assertThat(summary.getEntry())
        .extracting(Bundle.BundleEntryComponent::getResource)
        .containsExactly(patient, visit, systolic, weight);
  }

  @Test
  void summaryWithoutEncountersOrVitalsHasOnlyThePatient() {
    Patient patient = new Patient();
    when(patientService.getPatient("p1")).thenReturn(patient);
    when(encounterService.getEncountersForPatient("p1")).thenReturn(new Bundle());
    when(vitalsService.getVitals("p1", null)).thenReturn(new Bundle());

    assertThat(service.getSummary("p1").getEntry())
        .extracting(Bundle.BundleEntryComponent::getResource)
        .containsExactly(patient);
  }

  @Test
  void unknownPatientFailsTheSummary() {
    when(patientService.getPatient("missing"))
        .thenThrow(new ResourceNotFoundException("Patient", "missing"));

    assertThatThrownBy(() -> service.getSummary("missing"))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(encounterService, vitalsService);
  }
}
