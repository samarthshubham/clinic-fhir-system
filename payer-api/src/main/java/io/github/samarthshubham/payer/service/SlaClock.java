package io.github.samarthshubham.payer.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SlaClock {

  static final Duration EXPEDITED = Duration.ofHours(72);
  static final Duration STANDARD = Duration.ofDays(7);

  private final Clock clock;

  public SlaClock(Clock clock) {
    this.clock = clock;
  }

  public Instant now() {
    return clock.instant();
  }

  /** The submission time the clock runs from: the claim's created time, never in the future. */
  public Instant received(Instant created) {
    return received(created, now());
  }

  Instant received(Instant created, Instant now) {
    return created == null || created.isAfter(now) ? now : created;
  }

  Duration remaining(Instant deadline, Instant now) {
    return Duration.between(now, deadline);
  }

  public Instant decisionDeadline(Instant received, boolean expedited) {
    return received.plus(expedited ? EXPEDITED : STANDARD);
  }
}
