package io.github.samarthshubham.payer.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.payer.config.ImplementationGuideProperties.ImplementationGuide;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImplementationGuidePropertiesTest {

  @Test
  void canonicalsCarryThePinnedVersion() {
    ImplementationGuideProperties properties =
        new ImplementationGuideProperties(
            List.of(
                new ImplementationGuide(
                    "http://hl7.org/fhir/us/davinci-pas/ImplementationGuide/hl7.fhir.us.davinci-pas",
                    "2.2.1")));

    assertThat(properties.canonicals())
        .containsExactly(
            "http://hl7.org/fhir/us/davinci-pas/ImplementationGuide/hl7.fhir.us.davinci-pas|2.2.1");
  }

  @Test
  void missingListMeansNoImplementationGuides() {
    assertThat(new ImplementationGuideProperties(null).canonicals()).isEmpty();
  }
}
