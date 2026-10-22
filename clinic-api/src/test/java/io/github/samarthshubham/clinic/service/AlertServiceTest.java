package io.github.samarthshubham.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.clinic.dto.AlertResponse;
import io.github.samarthshubham.clinic.dto.ClinicianAlert;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.FlagMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.Instant;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Flag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertServiceTest {

  private static final ClinicianAlert ALERT =
      new ClinicianAlert("Patient/p1", "Observation/obs-9", "Systolic BP above 180 mmHg");
  private static final String ALERT_KEY_CRITERION =
      "identifier=" + FlagMapper.ALERT_IDENTIFIER_SYSTEM + "|obs-9";

  private final FlagMapper mapper = new FlagMapper();
  private FhirClientStub fhir;
  private AlertService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new AlertService(fhir.client(), mapper);
  }

  @Test
  void raiseStoresOneFlagPerObservation() {
    fhir.givenSearchResultWhen(Flag.class, new Bundle(), ALERT_KEY_CRITERION);

    assertThat(service.raise(ALERT)).isTrue();

    assertThat(fhir.created())
        .singleElement()
        .isInstanceOfSatisfying(
            Flag.class,
            flag -> assertThat(flag.getSubject().getReference()).isEqualTo("Patient/p1"));
  }

  @Test
  void raiseSkipsAnAlreadyStoredAlertSoAcknowledgedOnesStayClosed() {
    Bundle existing = new Bundle();
    existing.addEntry().setResource(acknowledgedFlag());
    fhir.givenSearchResultWhen(Flag.class, existing, ALERT_KEY_CRITERION);

    assertThat(service.raise(ALERT)).isFalse();

    assertThat(fhir.created()).isEmpty();
  }

  @Test
  void listsHighBpAlertsNewestFirstFilteredByStatus() {
    Bundle bundle = new Bundle().setTotal(1);
    Flag flag = mapper.toFhir(ALERT, Instant.parse("2026-10-22T10:00:00Z"));
    flag.setId("f1");
    bundle.addEntry().setResource(flag);
    fhir.givenSearchResult(Flag.class, bundle);

    var page = service.getAlerts("active");

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items()).extracting(AlertResponse::id).containsExactly("f1");
    assertThat(fhir.lastSearchCriteria(Flag.class))
        .containsExactly(
            "code=" + FlagMapper.ALERT_TYPE_SYSTEM + "|" + FlagMapper.HIGH_SYSTOLIC_BP,
            "status=active",
            "_sort=-date");
  }

  @Test
  void listsAllStatusesWhenNoneGiven() {
    fhir.givenSearchResult(Flag.class, new Bundle());

    service.getAlerts(null);

    assertThat(fhir.lastSearchCriteria(Flag.class)).noneMatch(c -> c.startsWith("status="));
  }

  @Test
  void acknowledgeClosesTheFlag() {
    Flag active = mapper.toFhir(ALERT, Instant.parse("2026-10-22T10:00:00Z"));
    fhir.givenStored(active, "f1");

    AlertResponse acknowledged = service.acknowledge("f1");

    assertThat(acknowledged.status()).isEqualTo("inactive");
    assertThat(fhir.updated())
        .singleElement()
        .isInstanceOfSatisfying(
            Flag.class,
            flag -> {
              assertThat(flag.getStatus()).isEqualTo(Flag.FlagStatus.INACTIVE);
              assertThat(flag.getPeriod().hasEnd()).isTrue();
            });
  }

  @Test
  void acknowledgingTwiceDoesNotUpdateAgain() {
    fhir.givenStored(acknowledgedFlag(), "f1");

    assertThat(service.acknowledge("f1").status()).isEqualTo("inactive");

    assertThat(fhir.updated()).isEmpty();
  }

  @Test
  void acknowledgingAnUnknownAlertIsNotFound() {
    assertThatThrownBy(() -> service.acknowledge("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Flag with id 'missing' not found");
  }

  private Flag acknowledgedFlag() {
    Flag flag = mapper.toFhir(ALERT, Instant.parse("2026-10-22T10:00:00Z"));
    flag.setStatus(Flag.FlagStatus.INACTIVE);
    return flag;
  }
}
