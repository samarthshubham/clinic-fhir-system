package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.ServiceRequestRequest;
import io.github.samarthshubham.clinic.dto.ServiceRequestResponse;
import java.time.ZoneId;
import java.util.Date;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.stereotype.Component;

@Component
public class ServiceRequestMapper {

  public ServiceRequest toFhir(ServiceRequestRequest request) {
    ServiceRequest sr = new ServiceRequest();
    sr.setSubject(new Reference("Patient/" + request.patientId()));
    sr.setStatus(ServiceRequest.ServiceRequestStatus.fromCode(request.status()));
    sr.setPriority(ServiceRequest.ServiceRequestPriority.fromCode(request.priority()));
    sr.setCode(new CodeableConcept().setText(request.code()));
    sr.addCategory(new CodeableConcept().setText(request.category()));
    if (request.encounterId() != null) {
      sr.setEncounter(new Reference("Encounter/" + request.encounterId()));
    }
    if (request.practitionerId() != null) {
      sr.setRequester(new Reference("Practitioner/" + request.practitionerId()));
    }
    Date authored =
        Date.from(request.authoredOn().atStartOfDay(ZoneId.systemDefault()).toInstant());
    sr.setAuthoredOnElement(new DateTimeType(authored));
    return sr;
  }

  public ServiceRequestResponse toResponse(ServiceRequest serviceRequest) {
    return new ServiceRequestResponse(
        FhirValues.idOf(serviceRequest),
        FhirValues.referenceId(serviceRequest.getSubject()),
        FhirValues.referenceId(serviceRequest.getEncounter()),
        FhirValues.referenceId(serviceRequest.getRequester()),
        serviceRequest.getCode().getText(),
        serviceRequest.hasCategory() ? serviceRequest.getCategoryFirstRep().getText() : null,
        serviceRequest.hasStatus() ? serviceRequest.getStatus().toCode() : null,
        serviceRequest.hasPriority() ? serviceRequest.getPriority().toCode() : null,
        FhirValues.localDate(serviceRequest.getAuthoredOn()));
  }
}
