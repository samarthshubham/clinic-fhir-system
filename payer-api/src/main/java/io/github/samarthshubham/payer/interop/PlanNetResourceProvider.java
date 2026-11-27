package io.github.samarthshubham.payer.interop;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.SearchEntryMode;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Resource;

public class PlanNetResourceProvider implements IResourceProvider {

  private static final int PAGE_SIZE = 50;
  private static final int MAX_PAGE_SIZE = 100;

  private final PlanNetDirectory directory;
  private final IGenericClient fhirClient;
  private final Class<? extends Resource> resourceType;
  private final String resourceName;

  PlanNetResourceProvider(
      PlanNetDirectory directory,
      IGenericClient fhirClient,
      Class<? extends Resource> resourceType,
      String resourceName) {
    this.directory = directory;
    this.fhirClient = fhirClient;
    this.resourceType = resourceType;
    this.resourceName = resourceName;
  }

  @Override
  public Class<? extends Resource> getResourceType() {
    return resourceType;
  }

  @Read(version = true)
  public Resource read(@IdParam IdType id) {
    // The current profile is the publication decision, including for a historic version.
    Resource current =
        PlanNetDirectory.fromStore(() -> fhirClient.read(resourceType, id.getIdPart()));
    requirePublished(current);
    if (!id.hasVersionIdPart()) {
      return publicResource(current);
    }
    Resource version =
        PlanNetDirectory.fromStore(
            () -> fhirClient.vread(resourceType, id.getIdPart(), id.getVersionIdPart()));
    requirePublished(version);
    return publicResource(version);
  }

  @Search(allowUnknownParams = true)
  public Bundle search(RequestDetails request) {
    return search(request.getFhirServerBase(), request.getParameters());
  }

  Bundle search(String serverBase, Map<String, String[]> parameters) {
    Map<String, String[]> query = directory.filterParameters(resourceName, parameters);
    int offset = getInt(query, "_offset", 0);
    int count = Math.min(MAX_PAGE_SIZE, getInt(query, "_count", PAGE_SIZE));
    query.put("_count", new String[] {Integer.toString(count)});
    query.put("_offset", new String[] {Integer.toString(offset)});
    String typeUrl = serverBase + "/" + resourceName;
    String upstream =
        searchUrl(resourceName, query)
            + "&_profile="
            + encode(String.join(",", directory.profiles(resourceName)));
    Bundle source =
        PlanNetDirectory.fromStore(
            () -> fhirClient.search().byUrl(upstream).returnBundle(Bundle.class).execute());

    Bundle bundle = new Bundle().setType(Bundle.BundleType.SEARCHSET);
    long matches = source.getEntry().stream().filter(PlanNetResourceProvider::isMatch).count();
    for (var entry : source.getEntry()) {
      if (!entry.hasResource() || !directory.isPublished(entry.getResource())) {
        continue;
      }
      Resource resource = publicResource(entry.getResource());
      bundle
          .addEntry()
          .setResource(resource)
          .setFullUrl(
              serverBase + "/" + resource.fhirType() + "/" + resource.getIdElement().getIdPart())
          .setSearch(entry.getSearch().copy());
    }
    if (source.hasTotal()
        && bundle.getEntry().stream().filter(PlanNetResourceProvider::isMatch).count() == matches) {
      bundle.setTotal(source.getTotal());
    }
    bundle.addLink().setRelation("self").setUrl(searchUrl(typeUrl, query));
    if (offset > 0 && count > 0) {
      addPage(bundle, typeUrl, query, "previous", Math.max(0, offset - count));
    }
    if (source.getLink("next") != null && matches > 0) {
      addPage(bundle, typeUrl, query, "next", offset + matches);
    }
    return bundle;
  }

  private static boolean isMatch(Bundle.BundleEntryComponent entry) {
    return !entry.getSearch().hasMode() || entry.getSearch().getMode() == SearchEntryMode.MATCH;
  }

  private static Resource publicResource(Resource resource) {
    Resource copy = resource.copy();
    removeConnectionHeaders(copy);
    return copy;
  }

  private static void removeConnectionHeaders(Resource resource) {
    // Connection headers may contain credentials supplied by the endpoint operator.
    if (resource instanceof Endpoint endpoint) {
      endpoint.setHeader(null);
    }
    if (resource instanceof DomainResource domain) {
      domain.getContained().forEach(PlanNetResourceProvider::removeConnectionHeaders);
    }
  }

  private void requirePublished(Resource resource) {
    if (!directory.isPublished(resource)) {
      throw new ResourceNotFoundException("Directory resource not found");
    }
  }

  private static void addPage(
      Bundle bundle, String base, Map<String, String[]> query, String link, long offset) {
    query.put("_offset", new String[] {Long.toString(offset)});
    bundle.addLink().setRelation(link).setUrl(searchUrl(base, query));
  }

  private static int getInt(Map<String, String[]> query, String key, int fallback) {
    return query.containsKey(key) ? Integer.parseInt(query.get(key)[0]) : fallback;
  }

  private static String searchUrl(String base, Map<String, String[]> query) {
    return base
        + "?"
        + query.entrySet().stream()
            .flatMap(
                entry ->
                    Arrays.stream(entry.getValue())
                        .map(value -> encode(entry.getKey()) + "=" + encode(value)))
            .collect(Collectors.joining("&"));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
