package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.ServiceRequestRequest;
import io.github.samarthshubham.clinic.dto.ServiceRequestResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.stereotype.Component;

@Component
public class ServiceRequestMapper {

  public ServiceRequest toFhir(ServiceRequestRequest request) {
    ServiceRequest sr = new ServiceRequest();
    sr.getMeta().addProfile(UsCoreProfiles.SERVICE_REQUEST);
    sr.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
    sr.setSubject(new Reference("Patient/" + request.patientId()));
    sr.setStatus(ServiceRequest.ServiceRequestStatus.fromCode(request.status()));
    sr.setPriority(ServiceRequest.ServiceRequestPriority.fromCode(request.priority()));
    CodeableConcept code = new CodeableConcept().setText(request.display());
    if (request.code() != null) {
      code.addCoding(new Coding().setSystem(request.codeSystem()).setCode(request.code()));
    }
    sr.setCode(code);
    sr.addCategory(new CodeableConcept().setText(request.category()));
    if (request.encounterId() != null) {
      sr.setEncounter(new Reference("Encounter/" + request.encounterId()));
    }
    if (request.practitionerId() != null) {
      sr.setRequester(new Reference("Practitioner/" + request.practitionerId()));
    }
    sr.setAuthoredOnElement(new DateTimeType(request.authoredOn().toString()));
    return sr;
  }

  public ServiceRequestResponse toResponse(ServiceRequest serviceRequest) {
    return new ServiceRequestResponse(
        FhirValues.idOf(serviceRequest),
        FhirValues.referenceId(serviceRequest.getSubject()),
        FhirValues.referenceId(serviceRequest.getEncounter()),
        FhirValues.referenceId(serviceRequest.getRequester()),
        serviceRequest.getCode().getText(),
        serviceRequest.getCode().hasCoding()
            ? serviceRequest.getCode().getCodingFirstRep().getSystem()
            : null,
        serviceRequest.getCode().hasCoding()
            ? serviceRequest.getCode().getCodingFirstRep().getCode()
            : null,
        serviceRequest.hasCategory() ? serviceRequest.getCategoryFirstRep().getText() : null,
        serviceRequest.hasStatus() ? serviceRequest.getStatus().toCode() : null,
        serviceRequest.hasPriority() ? serviceRequest.getPriority().toCode() : null,
        FhirValues.localDate(serviceRequest.getAuthoredOnElement()));
  }
}
