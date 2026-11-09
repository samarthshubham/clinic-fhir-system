package io.github.samarthshubham.clinic.support;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hl7.fhir.r4.model.Resource;

public final class GoldenFhir {

  private static final Path OUTPUT_DIR = Path.of("target", "fhir-out");
  private static final String FIXTURES = "/fixtures/davinci/";

  private GoldenFhir() {}

  public static void assertMatchesGolden(Resource resource, String fixture) {
    IParser parser = FhirContext.forR4Cached().newJsonParser().setPrettyPrint(true);
    String actual = parser.encodeResourceToString(resource);
    write(OUTPUT_DIR.resolve(fixture), actual);
    String expected =
        parser.encodeResourceToString(parser.parseResource(resource.getClass(), read(fixture)));
    assertThat(actual).as("mapper output vs golden fixture %s", fixture).isEqualTo(expected);
  }

  private static String read(String fixture) {
    try (InputStream in = GoldenFhir.class.getResourceAsStream(FIXTURES + fixture)) {
      if (in == null) {
        throw new AssertionError("Missing golden fixture fixtures/davinci/" + fixture);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void write(Path path, String json) {
    try {
      Files.createDirectories(path.getParent());
      Files.writeString(path, json + "\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
