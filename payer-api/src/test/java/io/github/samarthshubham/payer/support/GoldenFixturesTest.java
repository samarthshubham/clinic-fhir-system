package io.github.samarthshubham.payer.support;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.StrictErrorHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GoldenFixturesTest {

  private static final List<String> FOLDERS =
      List.of("provider", "crd", "dtr", "pas", "cdex", "pdex", "plan-net", "formulary");

  @Test
  void everyIgFolderIsOnTheTestClasspath() throws URISyntaxException {
    Path root = fixturesRoot();
    assertThat(FOLDERS).allSatisfy(folder -> assertThat(root.resolve(folder)).isDirectory());
  }

  @ParameterizedTest
  @MethodSource("goldenFiles")
  void goldenFileIsStrictlyParseableR4(Path file) throws IOException {
    IParser parser =
        FhirContext.forR4Cached().newJsonParser().setParserErrorHandler(new StrictErrorHandler());
    List<JsonNode> resources = fhirResources(new ObjectMapper().readTree(file.toFile()));
    assertThat(resources).as("FHIR resources in %s", file).isNotEmpty();
    for (JsonNode resource : resources) {
      assertThat(parser.parseResource(resource.toString())).isNotNull();
    }
  }

  @Test
  void cdsHooksJsonYieldsItsOutermostEmbeddedResources() throws IOException {
    JsonNode hook =
        new ObjectMapper()
            .readTree(
                "{\"context\":{\"draftOrders\":{\"resourceType\":\"Bundle\",\"entry\":[{\"resource\":"
                    + "{\"resourceType\":\"ServiceRequest\"}}]}},"
                    + "\"prefetch\":{\"patient\":{\"resourceType\":\"Patient\"},\"missing\":null}}");

    assertThat(fhirResources(hook))
        .extracting(resource -> resource.path("resourceType").asText())
        .containsExactly("Bundle", "Patient");
  }

  static List<JsonNode> fhirResources(JsonNode json) {
    List<JsonNode> resources = new ArrayList<>();
    collectOutermostResources(json, resources);
    return resources;
  }

  private static void collectOutermostResources(JsonNode node, List<JsonNode> resources) {
    if (node.isObject() && node.has("resourceType")) {
      resources.add(node);
    } else if (node.isContainerNode()) {
      node.forEach(child -> collectOutermostResources(child, resources));
    }
  }

  static Stream<Path> goldenFiles() throws IOException, URISyntaxException {
    try (Stream<Path> files = Files.walk(fixturesRoot())) {
      List<Path> json = files.filter(f -> f.toString().endsWith(".json")).sorted().toList();
      assertThat(json).isNotEmpty();
      return json.stream();
    }
  }

  private static Path fixturesRoot() throws URISyntaxException {
    return Path.of(GoldenFixturesTest.class.getResource("/fixtures/davinci").toURI());
  }
}
