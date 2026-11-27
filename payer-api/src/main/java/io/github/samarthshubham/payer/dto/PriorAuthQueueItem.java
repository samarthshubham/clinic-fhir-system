package io.github.samarthshubham.payer.dto;

import java.time.Instant;

public record PriorAuthQueueItem(
    String claimId,
    String authorizationReference,
    String memberId,
    String memberName,
    String service,
    boolean expedited,
    Instant decisionDeadline,
    long secondsRemaining,
    boolean breached) {}
