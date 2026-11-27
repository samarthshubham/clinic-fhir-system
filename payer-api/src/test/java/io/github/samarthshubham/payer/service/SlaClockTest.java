package io.github.samarthshubham.payer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SlaClockTest {

  private static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");

  private final SlaClock slaClock = new SlaClock(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void expeditedRequestsAreDueIn72Hours() {
    assertThat(slaClock.decisionDeadline(NOW, true)).isEqualTo("2026-11-22T08:00:00Z");
  }

  @Test
  void standardRequestsAreDueIn7Days() {
    assertThat(slaClock.decisionDeadline(NOW, false)).isEqualTo("2026-11-26T08:00:00Z");
  }

  @Test
  void aBackdatedSubmissionStartsTheClockWhenItWasMade() {
    Instant backdated = Instant.parse("2026-11-01T08:00:00Z");

    assertThat(slaClock.received(backdated)).isEqualTo(backdated);
    assertThat(slaClock.decisionDeadline(slaClock.received(backdated), false)).isBefore(NOW);
  }

  @Test
  void timeRemainingIsSignedAndZeroAtTheDeadline() {
    assertThat(slaClock.remaining(NOW.plusSeconds(60), slaClock.now()))
        .isEqualTo(Duration.ofMinutes(1));
    assertThat(slaClock.remaining(NOW, slaClock.now())).isZero();
    assertThat(slaClock.remaining(NOW.minusSeconds(60), slaClock.now()))
        .isEqualTo(Duration.ofMinutes(-1));
  }

  @Test
  void aSubmissionDatedInTheFutureOrNotAtAllStartsTheClockNow() {
    assertThat(slaClock.received(NOW.plusSeconds(3600))).isEqualTo(NOW);
    assertThat(slaClock.received(null)).isEqualTo(NOW);
  }
}
