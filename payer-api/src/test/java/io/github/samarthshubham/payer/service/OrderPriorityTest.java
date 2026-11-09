package io.github.samarthshubham.payer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderPriorityTest {

  @ParameterizedTest
  @CsvSource({
    "routine, ROUTINE, false",
    "urgent, URGENT, false",
    "asap, ASAP, false",
    "stat, STAT, true"
  })
  void mapsTheFhirRequestPriorityCode(String code, OrderPriority priority, boolean emergency) {
    assertThat(OrderPriority.fromCode(code)).isEqualTo(priority);
    assertThat(priority.isEmergency()).isEqualTo(emergency);
  }

  @Test
  void missingPriorityIsRoutine() {
    assertThat(OrderPriority.fromCode(null)).isEqualTo(OrderPriority.ROUTINE);
  }

  @Test
  void unknownPriorityIsRejected() {
    assertThatThrownBy(() -> OrderPriority.fromCode("whenever"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("whenever");
  }
}
