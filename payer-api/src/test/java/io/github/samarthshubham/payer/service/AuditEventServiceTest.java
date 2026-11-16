package io.github.samarthshubham.payer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import io.github.samarthshubham.payer.support.FhirClientStub;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.hl7.fhir.r4.model.AuditEvent;
import org.junit.jupiter.api.Test;

class AuditEventServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-11T09:30:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final FhirClientStub payerHapi = new FhirClientStub();
  private final AuditEventService service =
      new AuditEventService(payerHapi.client(), Runnable::run, CLOCK);

  @Test
  void successfulCallIsAuditedWithItsClientRequestAndPayerResources() {
    service.record(
        new AuditEventService.InteropAudit(
            "cds-services/order-sign-crd", "hook-1", "clinic-api", 200, List.of("Coverage/c1")));

    AuditEvent event = payerHapi.created(AuditEvent.class).getFirst();
    assertThat(event.getType().getCode()).isEqualTo("rest");
    assertThat(event.getAction()).isEqualTo(AuditEvent.AuditEventAction.E);
    assertThat(event.getRecorded().toInstant()).isEqualTo(NOW);
    assertThat(event.getOutcome()).isEqualTo(AuditEvent.AuditEventOutcome._0);
    assertThat(event.getAgentFirstRep().getRequestor()).isTrue();
    assertThat(event.getAgentFirstRep().getWho().getIdentifier().getSystem())
        .isEqualTo(AuditEventService.CLIENT_ID_SYSTEM);
    assertThat(event.getAgentFirstRep().getWho().getIdentifier().getValue())
        .isEqualTo("clinic-api");
    assertThat(event.getSource().getObserver().getDisplay()).isEqualTo("payer-api");
    assertThat(event.getEntity().get(0).getName()).isEqualTo("cds-services/order-sign-crd");
    assertThat(event.getEntity().get(0).getWhat().getIdentifier().getValue()).isEqualTo("hook-1");
    assertThat(event.getEntity().get(1).getWhat().getReference()).isEqualTo("Coverage/c1");
  }

  @Test
  void failuresAreAuditedWithTheirOutcome() {
    service.record(new AuditEventService.InteropAudit("x", null, null, 403, null));
    service.record(new AuditEventService.InteropAudit("x", null, null, 502, null));

    List<AuditEvent> events = payerHapi.created(AuditEvent.class);
    assertThat(events)
        .extracting(AuditEvent::getOutcome)
        .containsExactly(AuditEvent.AuditEventOutcome._4, AuditEvent.AuditEventOutcome._8);
    assertThat(events.getFirst().getAgentFirstRep().getWho().getDisplay()).isEqualTo("anonymous");
    assertThat(events.getFirst().getEntity()).hasSize(1);
    assertThat(events.getFirst().getEntityFirstRep().hasWhat()).isFalse();
  }

  @Test
  void aPayerHapiFailureDoesNotReachTheCaller() {
    IGenericClient failing = mock(IGenericClient.class);
    when(failing.create()).thenThrow(new FhirClientConnectionException("down"));
    AuditEventService failingService = new AuditEventService(failing, Runnable::run, CLOCK);

    assertThatCode(
            () ->
                failingService.record(
                    new AuditEventService.InteropAudit("x", "h", "c", 200, List.of())))
        .doesNotThrowAnyException();
  }
}
