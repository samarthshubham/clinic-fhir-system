package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.DateClientParam;
import ca.uhn.fhir.rest.gclient.QuantityClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import io.github.samarthshubham.clinic.config.ClinicalThresholds;
import io.github.samarthshubham.clinic.dto.ClinicianAlert;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
  public static final String SECRET_HEADER = "X-Alert-Secret";

  private static final String LOINC_SYSTEM = "http://loinc.org";
  private static final String SYSTOLIC_LOINC = "8480-6";
  private static final int READINGS_PER_PAGE = 50;

  private final IGenericClient fhirClient;
  private final ClinicalThresholds thresholds;
  private final List<Notifier> notifiers;
  private final String webhookUrl;
  private final String webhookSecret;
  private final Duration firstLookback;
  private Date lastAlertedUpdate;

  public HighBpAlertService(
      IGenericClient fhirClient,
      ClinicalThresholds thresholds,
      List<Notifier> notifiers,
      @Value("${clinic.alerts.webhook-url}") String webhookUrl,
      @Value("${clinic.alerts.webhook-secret}") String webhookSecret,
      @Value("${clinic.alerts.first-lookback}") Duration firstLookback) {
    this.fhirClient = fhirClient;
    this.thresholds = thresholds;
    this.notifiers = List.copyOf(notifiers);
    this.webhookUrl = webhookUrl;
    this.webhookSecret = webhookSecret;
    this.firstLookback = firstLookback;
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
        .setEndpoint(webhookUrl)
        .addHeader(SECRET_HEADER + ": " + webhookSecret);
    fhirClient.update().resource(subscription).execute();
  }

  public boolean isTrustedWebhookSecret(String presentedSecret) {
    return presentedSecret != null
        && MessageDigest.isEqual(
            presentedSecret.getBytes(StandardCharsets.UTF_8),
            webhookSecret.getBytes(StandardCharsets.UTF_8));
  }

  public synchronized void handleNotification() {
    Date updatedAfter =
        lastAlertedUpdate != null
            ? lastAlertedUpdate
            : Date.from(Instant.now().minus(firstLookback));
    boolean allSentSoFar = true;
    Bundle page = criticalSystolicReadingsAfter(updatedAfter);
    while (page != null) {
      for (Bundle.BundleEntryComponent entry : page.getEntry()) {
        Observation reading = (Observation) entry.getResource();
        boolean sent = send(reading);
        allSentSoFar = allSentSoFar && sent;
        Date readingUpdated = reading.getMeta().getLastUpdated();
        if (allSentSoFar && readingUpdated != null) {
          lastAlertedUpdate = readingUpdated;
        }
      }
      page =
          page.getLink(Bundle.LINK_NEXT) != null
              ? fhirClient.loadPage().next(page).execute()
              : null;
    }
  }

  private boolean send(Observation reading) {
    ClinicianAlert alert =
        new ClinicianAlert(
            reading.getSubject().getReference(),
            "Observation/" + reading.getIdElement().getIdPart(),
            criticalReason());
    boolean allSent = true;
    for (Notifier notifier : notifiers) {
      try {
        notifier.send(alert);
      } catch (RuntimeException e) {
        log.error(
            "Clinician alert via {} failed for {} ({}): {}",
            notifier.getClass().getSimpleName(),
            alert.patientReference(),
            alert.observationReference(),
            e.getClass().getSimpleName());
        allSent = false;
      }
    }
    return allSent;
  }

  private Bundle criticalSystolicReadingsAfter(Date updatedAfter) {
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
        .count(READINGS_PER_PAGE)
        .returnBundle(Bundle.class)
        .execute();
  }

  private String criticalReason() {
    return "Systolic BP above " + thresholds.systolicCritical() + " mmHg";
  }
}
