package io.github.samarthshubham.payer.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payer")
public record ImplementationGuideProperties(List<ImplementationGuide> implementationGuides) {

  public ImplementationGuideProperties {
    implementationGuides =
        implementationGuides == null ? List.of() : List.copyOf(implementationGuides);
  }

  public List<String> canonicals() {
    return implementationGuides.stream().map(ImplementationGuide::canonical).toList();
  }

  public record ImplementationGuide(String url, String version) {

    public String canonical() {
      return url + "|" + version;
    }
  }
}
