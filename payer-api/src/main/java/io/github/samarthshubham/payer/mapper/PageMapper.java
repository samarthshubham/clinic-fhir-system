package io.github.samarthshubham.payer.mapper;

import io.github.samarthshubham.payer.dto.PageResponse;
import java.util.List;
import java.util.function.Function;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

public final class PageMapper {

  private PageMapper() {}

  public static <R extends Resource> List<R> resources(Bundle bundle, Class<R> type) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(type::isInstance)
        .map(type::cast)
        .toList();
  }

  public static <R extends Resource, T> PageResponse<T> toPage(
      Bundle bundle, Class<R> type, Function<R, T> toResponse) {
    List<T> items = resources(bundle, type).stream().map(toResponse).toList();
    return new PageResponse<>(items, bundle.hasTotal() ? bundle.getTotal() : items.size());
  }
}
