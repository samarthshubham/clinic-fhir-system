package io.github.samarthshubham.payer.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.payer.service.ApprovalCriterion.Operator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ApprovalCriterionTest {

  @ParameterizedTest(name = "{0} {1} met by {2}: {3}")
  @CsvSource(
      nullValues = "null",
      value = {
        "EQUALS, true, true, true",
        "EQUALS, true, false, false",
        "AT_LEAST, 6, 6, true",
        "AT_LEAST, 6, 8.5, true",
        "AT_LEAST, 6, 4, false",
        "AT_LEAST, 6, six, false",
        "AT_MOST, 10, 10, true",
        "AT_MOST, 10, 11, false",
        "AT_MOST, 10, ten, false",
        "EQUALS, true, null, false"
      })
  void comparesTheAnswerWithTheOperator(
      Operator operator, String value, String answer, boolean met) {
    ApprovalCriterion criterion = new ApprovalCriterion("item", operator, value, null);

    assertThat(criterion.isMetBy(answer)).isEqualTo(met);
  }
}
