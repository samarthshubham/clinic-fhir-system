package io.github.samarthshubham.payer.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CoverageRuleStartupTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(CoverageRuleService.class);

  @Test
  void startsWithTheBundledRules() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(
                  context
                      .getBean(CoverageRuleService.class)
                      .findRule("http://loinc.org", "58410-2"))
              .extracting(CoverageRule::display)
              .isEqualTo("CBC panel - Blood by Automated count");
        });
  }

  @Test
  void invalidRulesFileFailsStartupWithAClearMessage() {
    contextRunner
        .withPropertyValues("payer.coverage-rules=classpath:coverage-rules-invalid.yaml")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                        "Invalid coverage rules in class path resource [coverage-rules-invalid.yaml]:"
                            + " rule 1 (http://www.ama-assn.org/go/cpt|72148): docNeeded needs a"
                            + " questionnaire canonical URL"));
  }
}
