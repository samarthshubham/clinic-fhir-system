package io.github.shubhamforge.clinic.service;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static io.github.shubhamforge.clinic.support.FhirClientStub.quantityObservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import io.github.shubhamforge.clinic.config.ClinicalThresholds;
import io.github.shubhamforge.clinic.dto.ClinicianAlert;
import io.github.shubhamforge.clinic.support.FhirClientStub;
import java.util.Date;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HighBpAlertServiceTest {

  private static final String WEBHOOK_URL = "http://clinic-api/api/alerts/bp-subscription";
  private static final ClinicalThresholds THRESHOLDS =
      new ClinicalThresholds(140, 180, 90, 120, 95.0, 90.0);

  @Mock private Notifier notifier;

  private FhirClientStub fhir;
  private HighBpAlertService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new HighBpAlertService(fhir.client(), THRESHOLDS, notifier, WEBHOOK_URL);
  }

  @Test
  void registersRestHookSubscriptionForCriticalSystolicReadings() {
    service.registerSubscription();

    Subscription subscription = fhir.stored(Subscription.class, HighBpAlertService.SUBSCRIPTION_ID);
    assertThat(subscription.getStatus()).isEqualTo(Subscription.SubscriptionStatus.REQUESTED);
    assertThat(subscription.getCriteria())
        .isEqualTo("Observation?code=http://loinc.org|8480-6&value-quantity=gt180");
    assertThat(subscription.getReason()).isEqualTo("Systolic BP above 180 mmHg");
    assertThat(subscription.getChannel().getType())
        .isEqualTo(Subscription.SubscriptionChannelType.RESTHOOK);
    assertThat(subscription.getChannel().getEndpoint()).isEqualTo(WEBHOOK_URL);
  }

  @Test
  void subscriptionAsksForEmptyNotificationsSoNoResourceContentIsSent() {
    service.registerSubscription();

    Subscription subscription = fhir.stored(Subscription.class, HighBpAlertService.SUBSCRIPTION_ID);
    assertThat(subscription.getChannel().hasPayload()).isFalse();
  }

  @Test
  void registeringTwiceUpdatesTheSameSubscriptionInsteadOfCreatingAnother() {
    service.registerSubscription();
    service.registerSubscription();

    assertThat(fhir.created()).isEmpty();
    assertThat(fhir.updated())
        .hasSize(2)
        .allSatisfy(
            resource ->
                assertThat(resource.getIdElement().getIdPart())
                    .isEqualTo(HighBpAlertService.SUBSCRIPTION_ID));
  }

  @Test
  void subscriptionCriteriaFollowConfiguredCriticalThreshold() {
    ClinicalThresholds stricter = new ClinicalThresholds(140, 170, 90, 120, 95.0, 90.0);
    new HighBpAlertService(fhir.client(), stricter, notifier, WEBHOOK_URL).registerSubscription();

    assertThat(fhir.stored(Subscription.class, HighBpAlertService.SUBSCRIPTION_ID).getCriteria())
        .endsWith("value-quantity=gt170");
  }

  @Test
  void startupRegistrationRegistersSubscription() {
    service.registerSubscriptionOnStartup();

    assertThat(fhir.stored(Subscription.class, HighBpAlertService.SUBSCRIPTION_ID)).isNotNull();
  }

  @Test
  void startupRegistrationToleratesUnreachableFhirServer() {
    IGenericClient unreachable = mock(IGenericClient.class);
    when(unreachable.update()).thenThrow(new FhirClientConnectionException("connection refused"));
    HighBpAlertService offline =
        new HighBpAlertService(unreachable, THRESHOLDS, notifier, WEBHOOK_URL);

    offline.registerSubscriptionOnStartup();

    verifyNoInteractions(notifier);
  }

  @Test
  void notificationAlertsWithOnlyPatientAndObservationReferences() {
    fhir.givenSearchResult(Observation.class, bundleOf(reading("obs-1", "Patient/p1", 1_000)));

    service.handleNotification();

    assertThat(sentAlerts())
        .containsExactly(
            new ClinicianAlert("Patient/p1", "Observation/obs-1", "Systolic BP above 180 mmHg"));
  }

  @Test
  void notificationSearchesCriticalSystolicReadingsOldestFirst() {
    long before = System.currentTimeMillis();

    service.handleNotification();

    List<String> criteria = fhir.lastSearchCriteria(Observation.class);
    assertThat(criteria)
        .startsWith("code=http://loinc.org|8480-6")
        .contains("_sort=_lastUpdated", "_count=50");
    assertThat(criteria.get(1)).startsWith("value-quantity=gt180");
    assertThat(criteria.get(2)).startsWith("_lastUpdated=gt");
    assertThat(lastUpdatedLowerBound(criteria).getTime()).isLessThan(before);
  }

  @Test
  void alertsEveryMatchingReadingInOrder() {
    fhir.givenSearchResult(
        Observation.class,
        bundleOf(reading("obs-1", "Patient/p1", 1_000), reading("obs-2", "Patient/p2", 2_000)));

    service.handleNotification();

    assertThat(sentAlerts())
        .extracting(ClinicianAlert::observationReference)
        .containsExactly("Observation/obs-1", "Observation/obs-2");
  }

  @Test
  void nextNotificationOnlyLooksAfterLastAlertedReading() {
    fhir.givenSearchResult(
        Observation.class,
        bundleOf(reading("obs-1", "Patient/p1", 1_000), reading("obs-2", "Patient/p2", 2_000)),
        new Bundle());

    service.handleNotification();
    service.handleNotification();

    List<String> secondSearch = fhir.lastSearchCriteria(Observation.class);
    assertThat(lastUpdatedLowerBound(secondSearch)).isEqualTo(new Date(2_000));
  }

  @Test
  void notificationWithoutNewReadingsSendsNothing() {
    service.handleNotification();

    verifyNoInteractions(notifier);
  }

  private List<ClinicianAlert> sentAlerts() {
    ArgumentCaptor<ClinicianAlert> captor = ArgumentCaptor.forClass(ClinicianAlert.class);
    verify(notifier, atLeastOnce()).send(captor.capture());
    return captor.getAllValues();
  }

  private static Date lastUpdatedLowerBound(List<String> criteria) {
    String value =
        criteria.stream()
            .filter(c -> c.startsWith("_lastUpdated=gt"))
            .findFirst()
            .orElseThrow()
            .substring("_lastUpdated=gt".length());
    return new InstantType(value).getValue();
  }

  private static Observation reading(String id, String patientReference, long lastUpdatedMillis) {
    Observation observation = quantityObservation(185);
    observation.setId(id);
    observation.setSubject(new Reference(patientReference));
    observation.getMeta().setLastUpdated(new Date(lastUpdatedMillis));
    return observation;
  }
}
