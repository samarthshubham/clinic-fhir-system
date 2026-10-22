package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static io.github.samarthshubham.clinic.support.FhirClientStub.quantityObservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import io.github.samarthshubham.clinic.config.ClinicalThresholds;
import io.github.samarthshubham.clinic.dto.ClinicianAlert;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class HighBpAlertServiceTest {

  private static final String WEBHOOK_URL = "http://clinic-api/api/alerts/bp-subscription";
  private static final ClinicalThresholds THRESHOLDS =
      new ClinicalThresholds(140, 180, 90, 120, 95.0, 90.0);
  private static final String SECRET = "test-secret";
  private static final Duration FIRST_LOOKBACK = Duration.ofHours(24);

  @Mock private Notifier notifier;

  private FhirClientStub fhir;
  private HighBpAlertService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service =
        new HighBpAlertService(
            fhir.client(), THRESHOLDS, List.of(notifier), WEBHOOK_URL, SECRET, FIRST_LOOKBACK);
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
    new HighBpAlertService(
            fhir.client(), stricter, List.of(notifier), WEBHOOK_URL, SECRET, FIRST_LOOKBACK)
        .registerSubscription();

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
        new HighBpAlertService(
            unreachable, THRESHOLDS, List.of(notifier), WEBHOOK_URL, SECRET, FIRST_LOOKBACK);

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
    assertThat(lastUpdatedLowerBound(criteria).getTime())
        .isBetween(before - FIRST_LOOKBACK.toMillis() - 1_000, before - FIRST_LOOKBACK.toMillis());
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
  void firstNotificationLookbackIsConfigurable() {
    HighBpAlertService shortLookback =
        new HighBpAlertService(
            fhir.client(),
            THRESHOLDS,
            List.of(notifier),
            WEBHOOK_URL,
            SECRET,
            Duration.ofMinutes(5));
    long before = System.currentTimeMillis();

    shortLookback.handleNotification();

    assertThat(lastUpdatedLowerBound(fhir.lastSearchCriteria(Observation.class)).getTime())
        .isBetween(before - Duration.ofMinutes(5).toMillis() - 1_000, before);
  }

  @Test
  void followsNextLinksUntilNoPagesAreLeft() {
    Bundle first = bundleOf(reading("obs-1", "Patient/p1", 1_000));
    first.addLink().setRelation(Bundle.LINK_NEXT).setUrl("page-2");
    Bundle second = bundleOf(reading("obs-2", "Patient/p2", 2_000));
    second.addLink().setRelation(Bundle.LINK_NEXT).setUrl("page-3");
    fhir.givenSearchResult(Observation.class, first);
    fhir.givenPage("page-2", second);
    fhir.givenPage("page-3", bundleOf(reading("obs-3", "Patient/p3", 3_000)));

    service.handleNotification();

    assertThat(fhir.loadedPages()).containsExactly("page-2", "page-3");
    assertThat(sentAlerts())
        .extracting(ClinicianAlert::observationReference)
        .containsExactly("Observation/obs-1", "Observation/obs-2", "Observation/obs-3");
  }

  @Test
  void singlePageWithoutNextLinkLoadsNoFurtherPages() {
    fhir.givenSearchResult(Observation.class, bundleOf(reading("obs-1", "Patient/p1", 1_000)));

    service.handleNotification();

    assertThat(fhir.loadedPages()).isEmpty();
  }

  @Test
  void cursorAdvancesAcrossPages() {
    Bundle first = bundleOf(reading("obs-1", "Patient/p1", 1_000));
    first.addLink().setRelation(Bundle.LINK_NEXT).setUrl("page-2");
    fhir.givenSearchResult(Observation.class, first, new Bundle());
    fhir.givenPage("page-2", bundleOf(reading("obs-2", "Patient/p2", 2_000)));

    service.handleNotification();
    service.handleNotification();

    assertThat(lastUpdatedLowerBound(fhir.lastSearchCriteria(Observation.class)))
        .isEqualTo(new Date(2_000));
  }

  @Test
  void readingWithoutLastUpdatedDoesNotMoveCursor() {
    Observation undated = reading("obs-2", "Patient/p2", 0);
    undated.getMeta().setLastUpdated(null);
    fhir.givenSearchResult(
        Observation.class, bundleOf(reading("obs-1", "Patient/p1", 1_000), undated), new Bundle());

    service.handleNotification();
    service.handleNotification();

    assertThat(lastUpdatedLowerBound(fhir.lastSearchCriteria(Observation.class)))
        .isEqualTo(new Date(1_000));
  }

  @Test
  void failedAlertIsLoggedWithReferencesAndRemainingReadingsAreStillSent(CapturedOutput output) {
    lenient()
        .doThrow(new IllegalStateException("gateway down"))
        .when(notifier)
        .send(argThat(alert -> alert.observationReference().equals("Observation/obs-2")));
    fhir.givenSearchResult(
        Observation.class,
        bundleOf(
            reading("obs-1", "Patient/p1", 1_000),
            reading("obs-2", "Patient/p2", 2_000),
            reading("obs-3", "Patient/p3", 3_000)));

    service.handleNotification();

    assertThat(sentAlerts())
        .extracting(ClinicianAlert::observationReference)
        .containsExactly("Observation/obs-1", "Observation/obs-2", "Observation/obs-3");
    assertThat(output).contains("failed for Patient/p2 (Observation/obs-2): IllegalStateException");
  }

  @Test
  void everyNotifierGetsTheAlertEvenWhenAnEarlierOneFails() {
    Notifier failing =
        alert -> {
          throw new IllegalStateException("store down");
        };
    HighBpAlertService fanOut =
        new HighBpAlertService(
            fhir.client(),
            THRESHOLDS,
            List.of(failing, notifier),
            WEBHOOK_URL,
            SECRET,
            FIRST_LOOKBACK);
    fhir.givenSearchResult(
        Observation.class, bundleOf(reading("obs-1", "Patient/p1", 1_000)), new Bundle());

    fanOut.handleNotification();
    fanOut.handleNotification();

    assertThat(sentAlerts())
        .extracting(ClinicianAlert::observationReference)
        .containsExactly("Observation/obs-1");
    assertThat(lastUpdatedLowerBound(fhir.lastSearchCriteria(Observation.class)))
        .isNotEqualTo(new Date(1_000));
  }

  @Test
  void cursorStopsBeforeFirstFailedAlertSoItIsRetried() {
    lenient()
        .doThrow(new IllegalStateException("gateway down"))
        .when(notifier)
        .send(argThat(alert -> alert.observationReference().equals("Observation/obs-2")));
    fhir.givenSearchResult(
        Observation.class,
        bundleOf(
            reading("obs-1", "Patient/p1", 1_000),
            reading("obs-2", "Patient/p2", 2_000),
            reading("obs-3", "Patient/p3", 3_000)),
        new Bundle());

    service.handleNotification();
    service.handleNotification();

    assertThat(lastUpdatedLowerBound(fhir.lastSearchCriteria(Observation.class)))
        .isEqualTo(new Date(1_000));
  }

  @Test
  void subscriptionSendsSharedSecretHeader() {
    service.registerSubscription();

    assertThat(
            fhir.stored(Subscription.class, HighBpAlertService.SUBSCRIPTION_ID)
                .getChannel()
                .getHeader())
        .extracting(StringType::getValue)
        .containsExactly("X-Alert-Secret: " + SECRET);
  }

  @Test
  void onlyTheConfiguredSecretIsTrusted() {
    assertThat(service.isTrustedWebhookSecret(SECRET)).isTrue();
    assertThat(service.isTrustedWebhookSecret("wrong")).isFalse();
    assertThat(service.isTrustedWebhookSecret(null)).isFalse();
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
