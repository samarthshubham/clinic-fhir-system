package io.github.shubhamforge.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.DateClientParam;
import ca.uhn.fhir.rest.gclient.QuantityClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import io.github.shubhamforge.clinic.config.ClinicalThresholds;
import io.github.shubhamforge.clinic.dto.ClinicianAlert;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Subscription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HighBpAlertService {

  static final String SUBSCRIPTION_ID = "clinic-high-systolic-bp";

  private static final String LOINC_SYSTEM = "http://loinc.org";
  private static final String SYSTOLIC_LOINC = "8480-6";
  private static final Duration FIRST_NOTIFICATION_LOOKBACK = Duration.ofMinutes(1);
  private static final int MAX_READINGS_PER_NOTIFICATION = 50;

  private final IGenericClient fhirClient;
  private final ClinicalThresholds thresholds;
  private final Notifier notifier;
  private final String webhookUrl;
  private Date lastAlertedUpdate;

  public HighBpAlertService(
      IGenericClient fhirClient,
      ClinicalThresholds thresholds,
      Notifier notifier,
      @Value("${clinic.alerts.webhook-url}") String webhookUrl) {
    this.fhirClient = fhirClient;
    this.thresholds = thresholds;
    this.notifier = notifier;
    this.webhookUrl = webhookUrl;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void registerSubscriptionOnStartup() {
    try {
      registerSubscription();
      log.info("High BP subscription registered, notifying {}", webhookUrl);
    } catch (BaseServerResponseException e) {
      log.warn("High BP subscription not registered, alerts disabled: {}", e.getMessage());
    }
  }

  public void registerSubscription() {
    Subscription subscription = new Subscription();
    subscription.setId(SUBSCRIPTION_ID);
    subscription.setStatus(Subscription.SubscriptionStatus.REQUESTED);
    subscription.setReason(criticalReason());
    subscription.setCriteria(
        "Observation?code="
            + LOINC_SYSTEM
            + "|"
            + SYSTOLIC_LOINC
            + "&value-quantity=gt"
            + thresholds.systolicCritical());
    subscription
        .getChannel()
        .setType(Subscription.SubscriptionChannelType.RESTHOOK)
        .setEndpoint(webhookUrl);
    fhirClient.update().resource(subscription).execute();
  }

  public synchronized void handleNotification() {
    Date updatedAfter =
        lastAlertedUpdate != null
            ? lastAlertedUpdate
            : Date.from(Instant.now().minus(FIRST_NOTIFICATION_LOOKBACK));
    for (Bundle.BundleEntryComponent entry : criticalSystolicReadingsAfter(updatedAfter)) {
      Observation reading = (Observation) entry.getResource();
      notifier.send(
          new ClinicianAlert(
              reading.getSubject().getReference(),
              "Observation/" + reading.getIdElement().getIdPart(),
              criticalReason()));
      lastAlertedUpdate = reading.getMeta().getLastUpdated();
    }
  }

  private List<Bundle.BundleEntryComponent> criticalSystolicReadingsAfter(Date updatedAfter) {
    return fhirClient
        .search()
        .forResource(Observation.class)
        .where(new TokenClientParam("code").exactly().systemAndCode(LOINC_SYSTEM, SYSTOLIC_LOINC))
        .and(
            new QuantityClientParam("value-quantity")
                .greaterThan()
                .number(thresholds.systolicCritical())
                .andNoUnits())
        .and(new DateClientParam("_lastUpdated").after().millis(updatedAfter))
        .sort()
        .ascending("_lastUpdated")
        .count(MAX_READINGS_PER_NOTIFICATION)
        .returnBundle(Bundle.class)
        .execute()
        .getEntry();
  }

  private String criticalReason() {
    return "Systolic BP above " + thresholds.systolicCritical() + " mmHg";
  }
}
