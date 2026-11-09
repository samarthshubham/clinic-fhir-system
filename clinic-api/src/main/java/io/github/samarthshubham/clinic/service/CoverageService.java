package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.samarthshubham.clinic.dto.CoverageRequest;
import io.github.samarthshubham.clinic.dto.CoverageResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.exception.ReferenceValidationException;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.CoverageMapper;
import io.github.samarthshubham.clinic.mapper.PageMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coverage;
import org.springframework.stereotype.Service;

@Service
public class CoverageService {

  private final IGenericClient fhirClient;
  private final CoverageMapper coverageMapper;

  public CoverageService(IGenericClient fhirClient, CoverageMapper coverageMapper) {
    this.fhirClient = fhirClient;
    this.coverageMapper = coverageMapper;
  }

  public CoverageResponse createCoverage(CoverageRequest request) {
    String payerOrganizationId = findOrCreatePayer(request);
    var outcome =
        fhirClient.create().resource(coverageMapper.toFhir(request, payerOrganizationId)).execute();
    String id = outcome.getId().getIdPart();
    return coverageMapper.toResponse(
        fhirClient.read().resource(Coverage.class).withId(id).execute());
  }

  public CoverageResponse getCoverage(String id) {
    return coverageMapper.toResponse(findCoverage(id));
  }

  public CoverageResponse updateCoverage(String id, CoverageRequest request) {
    if (!request.patientId().equals(getCoverage(id).patientId())) {
      throw new ReferenceValidationException("Coverage", id, request.patientId());
    }
    Coverage coverage = coverageMapper.toFhir(request, findOrCreatePayer(request));
    coverage.setId(id);
    fhirClient.update().resource(coverage).execute();
    return coverageMapper.toResponse(findCoverage(id));
  }

  public PageResponse<CoverageResponse> getCoveragesForPatient(String patientId) {
    return PageMapper.toPage(
        fhirClient
            .search()
            .forResource(Coverage.class)
            .where(new ReferenceClientParam("beneficiary").hasId("Patient/" + patientId))
            .returnBundle(Bundle.class)
            .execute(),
        Coverage.class,
        coverageMapper::toResponse);
  }

  public CoverageResponse findActiveCoverage(String patientId) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(Coverage.class)
            .where(new ReferenceClientParam("beneficiary").hasId("Patient/" + patientId))
            .and(new TokenClientParam("status").exactly().code("active"))
            .sort()
            .descending("_lastUpdated")
            .count(1)
            .returnBundle(Bundle.class)
            .execute();
    return PageMapper.toPage(bundle, Coverage.class, coverageMapper::toResponse).items().stream()
        .findFirst()
        .orElse(null);
  }

  private Coverage findCoverage(String id) {
    try {
      return fhirClient.read().resource(Coverage.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Coverage", id);
    }
  }

  private String findOrCreatePayer(CoverageRequest request) {
    return fhirClient
        .create()
        .resource(coverageMapper.toPayerOrganization(request))
        .conditionalByUrl(
            "Organization?identifier=" + CoverageMapper.PAYER_SYSTEM + "|" + request.payerId())
        .execute()
        .getId()
        .getIdPart();
  }
}
