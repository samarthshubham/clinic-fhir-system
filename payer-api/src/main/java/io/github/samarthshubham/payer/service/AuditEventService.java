package io.github.samarthshubham.payer.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class AuditEventService {

  public static final String CLIENT_ID_SYSTEM =
      "http://praxishealthplan.example/fhir/identifier/client-id";

  private static final Logger log = LoggerFactory.getLogger(AuditEventService.class);

  private final IGenericClient fhirClient;
  private final TaskExecutor executor;
  private final Clock clock;

  public AuditEventService(
      IGenericClient fhirClient,
      @Qualifier("applicationTaskExecutor") TaskExecutor executor,
      Clock clock) {
    this.fhirClient = fhirClient;
    this.executor = executor;
    this.clock = clock;
  }

  /** Writes the AuditEvent in the background, so a slow payer HAPI never delays the caller. */
  public void record(InteropAudit audit) {
    AuditEvent event = toAuditEvent(audit);
    executor.execute(() -> write(event, audit));
  }

  AuditEvent toAuditEvent(InteropAudit audit) {
    AuditEvent event = new AuditEvent();
    event.setType(
        new Coding(
            "http://terminology.hl7.org/CodeSystem/audit-event-type", "rest", "RESTful Operation"));
    event.addSubtype(new Coding("http://hl7.org/fhir/restful-interaction", "operation", null));
    event.setAction(AuditEvent.AuditEventAction.E);
    event.setRecorded(Date.from(clock.instant()));
    event.setOutcome(outcome(audit.httpStatus()));
    event.setOutcomeDesc("HTTP " + audit.httpStatus());
    AuditEvent.AuditEventAgentComponent agent = event.addAgent().setRequestor(true);
    if (audit.clientId() != null) {
      agent.setWho(
          new Reference()
              .setIdentifier(
                  new Identifier().setSystem(CLIENT_ID_SYSTEM).setValue(audit.clientId())));
    } else {
      agent.setWho(new Reference().setDisplay("anonymous"));
    }
    event.getSource().setObserver(new Reference().setDisplay("payer-api"));
    AuditEvent.AuditEventEntityComponent request = event.addEntity().setName(audit.interaction());
    if (audit.requestId() != null) {
      request.setWhat(new Reference().setIdentifier(new Identifier().setValue(audit.requestId())));
    }
    for (String reference : audit.references()) {
      event.addEntity().setWhat(new Reference(reference));
    }
    return event;
  }

  private void write(AuditEvent event, InteropAudit audit) {
    try {
      fhirClient.create().resource(event).execute();
    } catch (RuntimeException e) {
      log.warn(
          "Could not write the AuditEvent for {} {}: {}",
          audit.interaction(),
          audit.requestId(),
          e.getMessage());
    }
  }

  private static AuditEvent.AuditEventOutcome outcome(int httpStatus) {
    if (httpStatus < 400) {
      return AuditEvent.AuditEventOutcome._0;
    }
    return httpStatus < 500 ? AuditEvent.AuditEventOutcome._4 : AuditEvent.AuditEventOutcome._8;
  }

  public record InteropAudit(
      String interaction,
      String requestId,
      String clientId,
      int httpStatus,
      List<String> references) {

    public InteropAudit {
      references = references == null ? List.of() : List.copyOf(references);
    }
  }
}
