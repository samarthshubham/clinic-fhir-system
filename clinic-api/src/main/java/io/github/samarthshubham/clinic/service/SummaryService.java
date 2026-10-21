package io.github.samarthshubham.clinic.service;

import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Service;

@Service
public class SummaryService {

  private final PatientService patientService;
  private final EncounterService encounterService;
  private final VitalsService vitalsService;

  public SummaryService(
      PatientService patientService,
      EncounterService encounterService,
      VitalsService vitalsService) {
    this.patientService = patientService;
    this.encounterService = encounterService;
    this.vitalsService = vitalsService;
  }

  public Bundle getSummary(String patientId) {
    Bundle summary = new Bundle();
    summary.setType(Bundle.BundleType.COLLECTION);
    summary.addEntry().setResource(patientService.getPatient(patientId));
    encounterService
        .getEncountersForPatient(patientId)
        .getEntry()
        .forEach(e -> summary.addEntry().setResource(e.getResource()));
    vitalsService
        .getVitals(patientId, null)
        .getEntry()
        .forEach(e -> summary.addEntry().setResource(e.getResource()));
    return summary;
  }
}
