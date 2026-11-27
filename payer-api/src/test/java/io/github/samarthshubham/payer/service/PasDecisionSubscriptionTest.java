package io.github.samarthshubham.payer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IUpdate;
import ca.uhn.fhir.rest.gclient.IUpdateTyped;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import org.hl7.fhir.r4.model.Subscription;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PasDecisionSubscriptionTest {

  private static final String ENDPOINT = "https://clinic.test/api/alerts/prior-auth-subscription";
  private final IGenericClient fhir = mock(IGenericClient.class);

  @Test
  void registersAStablePayloadlessRestHookAndRetriesAStartupFailure() {
    var update = mock(IUpdate.class);
    var write = mock(IUpdateTyped.class);
    when(fhir.update()).thenReturn(update);
    when(update.resource(any(Subscription.class))).thenReturn(write);
    when(write.execute()).thenThrow(new InternalErrorException("not ready")).thenReturn(null);
    var bootstrap = new PasDecisionSubscription(fhir, ENDPOINT, "test-secret");

    bootstrap.register();
    bootstrap.register();
    bootstrap.register();

    var capture = ArgumentCaptor.forClass(Subscription.class);
    verify(update, times(2)).resource(capture.capture());
    Subscription subscription = capture.getValue();
    assertThat(subscription.getIdPart()).isEqualTo(PasDecisionSubscription.ID);
    assertThat(subscription.getStatus()).isEqualTo(Subscription.SubscriptionStatus.REQUESTED);
    assertThat(subscription.getCriteria()).isEqualTo("ClaimResponse?use=preauthorization");
    assertThat(subscription.getChannel().getType())
        .isEqualTo(Subscription.SubscriptionChannelType.RESTHOOK);
    assertThat(subscription.getChannel().getEndpoint()).isEqualTo(ENDPOINT);
    assertThat(subscription.getChannel().hasPayload()).isFalse();
    assertThat(subscription.getChannel().getHeader().getFirst().getValue())
        .isEqualTo("X-PAS-Decision-Secret: test-secret");
  }

  @Test
  void permitsUnconfiguredSubscriptionsButRequiresASecretWhenEnabled() {
    new PasDecisionSubscription(fhir, "", "").register();
    verifyNoInteractions(fhir);
    assertThatThrownBy(() -> new PasDecisionSubscription(fhir, ENDPOINT, ""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PasDecisionSubscription(fhir, ENDPOINT, "bad\nsecret"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
