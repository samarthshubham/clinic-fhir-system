package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.dto.ServiceRequestRequest;
import io.github.samarthshubham.clinic.dto.ServiceRequestResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.PageMapper;
import io.github.samarthshubham.clinic.mapper.ServiceRequestMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.stereotype.Service;

@Service
public class ServiceRequestService {

  private final IGenericClient fhirClient;
  private final ServiceRequestMapper serviceRequestMapper;

  public ServiceRequestService(
      IGenericClient fhirClient, ServiceRequestMapper serviceRequestMapper) {
    this.fhirClient = fhirClient;
    this.serviceRequestMapper = serviceRequestMapper;
  }

  public ServiceRequestResponse createServiceRequest(ServiceRequestRequest request) {
    var outcome = fhirClient.create().resource(serviceRequestMapper.toFhir(request)).execute();
    String id = outcome.getId().getIdPart();
    return serviceRequestMapper.toResponse(
        fhirClient.read().resource(ServiceRequest.class).withId(id).execute());
  }

  public ServiceRequestResponse getServiceRequest(String id) {
    return serviceRequestMapper.toResponse(findServiceRequest(id));
  }

  public ServiceRequest findServiceRequest(String id) {
    try {
      return fhirClient.read().resource(ServiceRequest.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("ServiceRequest", id);
    }
  }

  public PageResponse<ServiceRequestResponse> getServiceRequestsForPatient(
      String patientId, String status) {
    var query =
        fhirClient
            .search()
            .forResource(ServiceRequest.class)
            .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
            .returnBundle(Bundle.class);
    if (status != null && !status.isBlank()) {
      query = query.and(new TokenClientParam("status").exactly().code(status));
    }
    return PageMapper.toPage(
        query.execute(), ServiceRequest.class, serviceRequestMapper::toResponse);
  }

  public Bundle findPendingServiceRequestsForPatient(String patientId) {
    return fhirClient
        .search()
        .forResource(ServiceRequest.class)
        .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
        .and(new TokenClientParam("status").exactly().code("active"))
        .count(5)
        .returnBundle(Bundle.class)
        .execute();
  }

  public void complete(String id) {
    ServiceRequest sr = findServiceRequest(id);
    sr.setStatus(ServiceRequest.ServiceRequestStatus.COMPLETED);
    fhirClient.update().resource(sr).execute();
  }
}
