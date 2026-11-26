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
    Instant now = now();
    return created == null || created.isAfter(now) ? now : created;
  }

  public Instant decisionDeadline(Instant received, boolean expedited) {
    return received.plus(expedited ? EXPEDITED : STANDARD);
  }
}
