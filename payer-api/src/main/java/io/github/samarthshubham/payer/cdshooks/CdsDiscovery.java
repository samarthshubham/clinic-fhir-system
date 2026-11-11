package io.github.samarthshubham.payer.cdshooks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record CdsDiscovery(List<Service> services) {

  static final String CRD_VERSION_EXTENSION = "davinci-crd.version";
  static final String CRD_VERSION = "2.2";

  public static CdsDiscovery of(CrdHook... hooks) {
    return new CdsDiscovery(
        Arrays.stream(hooks)
            .map(
                hook ->
                    new Service(
                        hook.hook(),
                        hook.title(),
                        hook.description(),
                        hook.serviceId(),
                        hook.prefetch(),
                        Map.of(CRD_VERSION_EXTENSION, List.of(CRD_VERSION))))
            .toList());
  }

  public record Service(
      String hook,
      String title,
      String description,
      String id,
      Map<String, String> prefetch,
      Map<String, List<String>> extension) {}
}
