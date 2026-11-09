package io.github.samarthshubham.payer.service;

import java.util.Arrays;

public enum OrderPriority {
  ROUTINE("routine"),
  URGENT("urgent"),
  ASAP("asap"),
  STAT("stat");

  private final String code;

  OrderPriority(String code) {
    this.code = code;
  }

  public static OrderPriority fromCode(String code) {
    if (code == null) {
      return ROUTINE;
    }
    return Arrays.stream(values())
        .filter(priority -> priority.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown order priority: " + code));
  }

  public boolean isEmergency() {
    return this == STAT;
  }
}
