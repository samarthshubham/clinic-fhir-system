package io.github.samarthshubham.payer.dto;

import java.util.List;

public record PriorAuthReviewResponse(
    String claimId,
    String authorizationReference,
    String disposition,
    boolean pended,
    List<ClaimLine> lines,
    List<Answer> answers,
    List<Attachment> attachments) {

  public record ClaimLine(int sequence, String service, String code, Rule rule) {}

  public record Rule(String name, boolean requiresPriorAuth, List<Criterion> criteria) {}

  public record Criterion(String linkId, String operator, String expected, boolean matched) {}

  public record Answer(String linkId, String question, List<String> values) {}

  public record Attachment(String title, String contentType, Integer size) {}
}
