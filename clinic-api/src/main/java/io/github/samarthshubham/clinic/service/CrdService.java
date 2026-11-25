package io.github.samarthshubham.clinic.service;

import io.github.samarthshubham.clinic.client.CrdClient;
import io.github.samarthshubham.clinic.client.CrdResult;
import io.github.samarthshubham.clinic.config.PayerApiProperties;
import io.github.samarthshubham.clinic.mapper.CoverageMapper;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.stereotype.Service;

@Service
public class CrdService {

  private final CrdClient crdClient;
  private final CoverageService coverageService;
  private final PatientService patientService;
  private final PractitionerService practitionerService;
  private final PayerApiProperties payerApiProperties;

  public CrdService(
      CrdClient crdClient,
      CoverageService coverageService,
      PatientService patientService,
      PractitionerService practitionerService,
      PayerApiProperties payerApiProperties) {
    this.crdClient = crdClient;
    this.coverageService = coverageService;
    this.patientService = patientService;
    this.practitionerService = practitionerService;
    this.payerApiProperties = payerApiProperties;
  }

  /**
   * Calls order-sign for the unsaved order and links it to the patient's active Coverage. Skipped
   * unless that Coverage is with the configured payer, so no patient data goes to another payer.
   */
  public CrdResult checkOrder(ServiceRequest order, String patientId, String practitionerId) {
    Coverage coverage = activeCoverageWithPayer(patientId);
    if (coverage == null) {
      return CrdResult.none();
    }
    order.getInsurance().clear();
    order.addInsurance(new Reference("Coverage/" + coverage.getIdPart()));
    return crdClient.orderSign(
        order, patientService.findPatient(patientId), coverage, practitioner(practitionerId));
  }

  public CrdResult checkAppointment(
      Appointment appointment, String patientId, String practitionerId) {
    Coverage coverage = activeCoverageWithPayer(patientId);
    if (coverage == null) {
      return CrdResult.none();
    }
    return crdClient.appointmentBook(
        appointment, patientService.findPatient(patientId), coverage, practitioner(practitionerId));
  }

  public Coverage activeCoverageWithPayer(String patientId) {
    Coverage coverage = coverageService.findActiveCoverageResource(patientId);
    if (coverage == null) {
      return null;
    }
    Identifier payer = coverage.getPayorFirstRep().getIdentifier();
    boolean withPayer =
        CoverageMapper.PAYER_SYSTEM.equals(payer.getSystem())
            && payerApiProperties.payerId().equals(payer.getValue());
    return withPayer ? coverage : null;
  }

  private Practitioner practitioner(String practitionerId) {
    return practitionerId == null ? null : practitionerService.findPractitioner(practitionerId);
  }
}
