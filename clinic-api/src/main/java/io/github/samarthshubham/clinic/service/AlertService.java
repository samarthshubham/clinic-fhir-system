package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.samarthshubham.clinic.dto.AlertResponse;
import io.github.samarthshubham.clinic.dto.ClinicianAlert;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.FlagMapper;
import io.github.samarthshubham.clinic.mapper.PageMapper;
import java.time.Instant;
import java.util.Date;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Flag;
import org.springframework.stereotype.Service;

@Service
public class AlertService {

  private final IGenericClient fhirClient;
  private final FlagMapper flagMapper;

  public AlertService(IGenericClient fhirClient, FlagMapper flagMapper) {
    this.fhirClient = fhirClient;
    this.flagMapper = flagMapper;
  }

  public boolean raise(ClinicianAlert alert) {
    Bundle existing =
        fhirClient
            .search()
            .forResource(Flag.class)
            .where(
                new TokenClientParam("identifier")
                    .exactly()
                    .systemAndCode(FlagMapper.ALERT_IDENTIFIER_SYSTEM, flagMapper.alertKey(alert)))
            .returnBundle(Bundle.class)
            .execute();
    if (!existing.getEntry().isEmpty()) {
      return false;
    }
    fhirClient.create().resource(flagMapper.toFhir(alert, Instant.now())).execute();
    return true;
  }

  public PageResponse<AlertResponse> getAlerts(String status) {
    var query =
        fhirClient
            .search()
            .forResource(Flag.class)
            .where(
                new TokenClientParam("code")
                    .exactly()
                    .systemAndCode(FlagMapper.ALERT_TYPE_SYSTEM, FlagMapper.HIGH_SYSTOLIC_BP));
    if (status != null) {
      query = query.and(new TokenClientParam("status").exactly().code(status));
    }
    Bundle bundle = query.sort().descending("date").returnBundle(Bundle.class).execute();
    return PageMapper.toPage(bundle, Flag.class, flagMapper::toResponse);
  }

  public AlertResponse acknowledge(String id) {
    Flag flag;
    try {
      flag = fhirClient.read().resource(Flag.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Flag", id);
    }
    if (flag.getStatus() != Flag.FlagStatus.INACTIVE) {
      flag.setStatus(Flag.FlagStatus.INACTIVE);
      flag.getPeriod().setEnd(Date.from(Instant.now()));
      fhirClient.update().resource(flag).execute();
    }
    return flagMapper.toResponse(flag);
  }
}
