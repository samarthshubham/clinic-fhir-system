package io.github.samarthshubham.payer.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PasDecisionSubscription {

  private static final Logger log = LoggerFactory.getLogger(PasDecisionSubscription.class);

  static final String ID = "clinic-pas-decisions";

  private final IGenericClient fhirClient;
  private final String webhookUrl;
  private final String webhookSecret;
  private boolean registered;

  public PasDecisionSubscription(
      IGenericClient fhirClient,
      @Value("${payer.pas.decision-webhook-url}") String webhookUrl,
      @Value("${payer.pas.decision-webhook-secret}") String webhookSecret) {
    if (webhookUrl.isBlank() != webhookSecret.isBlank()
        || webhookSecret.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("Configure both PAS decision webhook URL and secret");
    }
    this.fhirClient = fhirClient;
    this.webhookUrl = webhookUrl;
    this.webhookSecret = webhookSecret;
  }

  @Scheduled(fixedDelayString = "30s")
  public void register() {
    if (registered || webhookUrl.isBlank()) {
      return;
    }
    Subscription subscription =
        new Subscription()
            .setStatus(Subscription.SubscriptionStatus.REQUESTED)
            .setCriteria("ClaimResponse?use=preauthorization")
            .setReason("Refresh clinic prior authorization decisions");
    subscription.setId(ID);
    subscription
        .getChannel()
        .setType(Subscription.SubscriptionChannelType.RESTHOOK)
        .setEndpoint(webhookUrl)
        .addHeader("X-PAS-Decision-Secret: " + webhookSecret);
    try {
      fhirClient.update().resource(subscription).execute();
      registered = true;
      log.info("Clinic PAS decision subscription registered");
    } catch (RuntimeException e) {
      log.warn(
          "Clinic PAS decision subscription registration failed: {}", e.getClass().getSimpleName());
    }
  }
}
