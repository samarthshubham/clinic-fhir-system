package io.github.samarthshubham.payer.service;

import java.math.BigDecimal;

public record ApprovalCriterion(
    String linkId, Operator operator, String value, DenialReason denialReason) {

  public enum Operator {
    EQUALS,
    AT_LEAST,
    AT_MOST
  }

  public record DenialReason(String code, String text) {}

  public boolean isMetBy(String answer) {
    if (answer == null) {
      return false;
    }
    return switch (operator) {
      case EQUALS -> value.equals(answer);
      case AT_LEAST -> isNumber(answer) && new BigDecimal(answer).compareTo(numericValue()) >= 0;
      case AT_MOST -> isNumber(answer) && new BigDecimal(answer).compareTo(numericValue()) <= 0;
    };
  }

  boolean isNumericOperator() {
    return operator == Operator.AT_LEAST || operator == Operator.AT_MOST;
  }

  private BigDecimal numericValue() {
    return new BigDecimal(value);
  }

  static boolean isNumber(String text) {
    try {
      new BigDecimal(text);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
