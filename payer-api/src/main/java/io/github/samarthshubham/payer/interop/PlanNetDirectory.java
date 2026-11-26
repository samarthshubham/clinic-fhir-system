package io.github.samarthshubham.payer.interop;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceGoneException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnclassifiedServerFailureException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
@Interceptor
public class PlanNetDirectory {

  private static final Set<RestOperationTypeEnum> PUBLIC_OPERATIONS =
      Set.of(
          RestOperationTypeEnum.READ,
          RestOperationTypeEnum.VREAD,
          RestOperationTypeEnum.SEARCH_TYPE);

  private final Map<String, CapabilityStatementRestResourceComponent> catalog;
  private final List<IResourceProvider> providers;
  private final PlanNetResourceProvider practitionerRoles;

  public PlanNetDirectory(FhirContext context, IGenericClient fhirClient) {
    // The profile and search parameter lists come from the Plan-Net 1.2.0 CapabilityStatement.
    try (InputStream source = new ClassPathResource("plan-net-directory.json").getInputStream()) {
      CapabilityStatement capability =
          context.newJsonParser().parseResource(CapabilityStatement.class, source);
      catalog =
          capability.getRestFirstRep().getResource().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      CapabilityStatementRestResourceComponent::getType, Function.identity()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    practitionerRoles =
        new PlanNetResourceProvider(this, fhirClient, PractitionerRole.class, "PractitionerRole");
    providers =
        catalog.keySet().stream()
            .sorted()
            .map(
                type ->
                    "PractitionerRole".equals(type)
                        ? practitionerRoles
                        : new PlanNetResourceProvider(
                            this,
                            fhirClient,
                            context
                                .getResourceDefinition(type)
                                .getImplementingClass()
                                .asSubclass(Resource.class),
                            type))
            .map(IResourceProvider.class::cast)
            .toList();
  }

  public List<IResourceProvider> providers() {
    return providers;
  }

  public Bundle practitionerRoles(int offset) {
    return practitionerRoles.search(
        "/fhir",
        Map.of(
            "_offset", new String[] {Integer.toString(offset)},
            "_include",
                new String[] {
                  "PractitionerRole:practitioner",
                  "PractitionerRole:location",
                  "PractitionerRole:organization"
                }));
  }

  boolean isPublicRequest(RequestDetails request) {
    return request.getRestOperationType() != null
        && PUBLIC_OPERATIONS.contains(request.getRestOperationType())
        && request.getResourceName() != null
        && catalog.containsKey(request.getResourceName());
  }

  Set<String> types() {
    return catalog.keySet();
  }

  boolean isPublished(Resource resource) {
    return containsOnlyDirectoryTypes(resource)
        && resource.getMeta().getProfile().stream()
            .map(CanonicalType::getValue)
            .anyMatch(profile -> profiles(resource.fhirType()).contains(profile));
  }

  private boolean containsOnlyDirectoryTypes(Resource resource) {
    return catalog.containsKey(resource.fhirType())
        && (!(resource instanceof DomainResource domain)
            || domain.getContained().stream().allMatch(this::containsOnlyDirectoryTypes));
  }

  List<String> profiles(String type) {
    return catalog.get(type).getSupportedProfile().stream().map(CanonicalType::getValue).toList();
  }

  Map<String, String[]> filterParameters(String type, Map<String, String[]> parameters) {
    Map<String, String[]> filters = new LinkedHashMap<>(parameters);
    filters.remove("_format");
    filters.remove("_pretty");
    for (var parameter : filters.entrySet()) {
      String key = parameter.getKey();
      boolean valid =
          switch (key) {
            case "_count", "_offset" ->
                parameter.getValue().length == 1 && parameter.getValue()[0].matches("[0-9]{1,9}");
            case "_include" -> includes(catalog.get(type).getSearchInclude(), parameter.getValue());
            case "_revinclude" ->
                includes(catalog.get(type).getSearchRevInclude(), parameter.getValue());
            default ->
                key.startsWith("_has:") ? isReverseChain(type, key) : isSearchParameter(type, key);
          };
      if (!valid) {
        throw new InvalidRequestException("Unsupported Plan-Net search parameter");
      }
    }
    return filters;
  }

  private static boolean includes(List<StringType> allowed, String[] supplied) {
    List<String> entries = allowed.stream().map(StringType::getValue).toList();
    return List.of(supplied).stream().allMatch(entries::contains);
  }

  private boolean isSearchParameter(String type, String key) {
    int dot = key.indexOf('.');
    String head = dot < 0 ? key : key.substring(0, dot);
    String[] selector = head.split(":", -1);
    if (selector.length > 2) {
      return false;
    }
    var parameter = parameter(type, selector[0]);
    if (parameter == null) {
      return false;
    }
    List<String> targets = extensionCodes(parameter, "target");
    if (dot >= 0) {
      String chain = key.substring(dot + 1);
      return extensionCodes(parameter, "chain").contains(chain)
          && (selector.length == 1 || targets.contains(selector[1]));
    }
    if (selector.length == 1) {
      return true;
    }
    return switch (parameter.getType()) {
      case STRING -> Set.of("exact", "contains").contains(selector[1]);
      case REFERENCE -> targets.contains(selector[1]);
      default -> false;
    };
  }

  private boolean isReverseChain(String type, String key) {
    String[] parts = key.split(":", 4);
    if (parts.length != 4 || !catalog.containsKey(parts[1])) {
      return false;
    }
    var reference = parameter(parts[1], parts[2]);
    return reference != null
        && extensionCodes(reference, "target").contains(type)
        && !extensionCodes(reference, "chain").isEmpty()
        && isSearchParameter(parts[1], parts[3]);
  }

  private CapabilityStatementRestResourceSearchParamComponent parameter(String type, String name) {
    return catalog.get(type).getSearchParam().stream()
        .filter(candidate -> name.equals(candidate.getName()))
        .findFirst()
        .orElse(null);
  }

  private static List<String> extensionCodes(
      CapabilityStatementRestResourceSearchParamComponent parameter, String url) {
    return parameter.getExtensionsByUrl(url).stream()
        .map(extension -> extension.getValue().primitiveValue())
        .toList();
  }

  @Hook(Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED)
  public void describeDirectory(IBaseConformance conformance) {
    CapabilityStatement statement = (CapabilityStatement) conformance;
    for (var resource : statement.getRestFirstRep().getResource()) {
      var specification = catalog.get(resource.getType());
      if (specification != null) {
        var copy = specification.copy();
        resource.setSupportedProfile(copy.getSupportedProfile());
        resource.setReferencePolicy(copy.getReferencePolicy());
        copy.getSearchParam().forEach(parameter -> parameter.getExtension().clear());
        resource.setSearchParam(copy.getSearchParam());
        resource.setSearchInclude(copy.getSearchInclude());
        resource.setSearchRevInclude(copy.getSearchRevInclude());
      }
    }
  }

  static <T> T fromStore(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (FhirClientConnectionException e) {
      throw new UnclassifiedServerFailureException(503, "Directory store is unavailable");
    } catch (ResourceNotFoundException | ResourceGoneException e) {
      throw new ResourceNotFoundException("Directory resource not found");
    } catch (InvalidRequestException e) {
      throw new InvalidRequestException("Invalid Plan-Net query");
    } catch (BaseServerResponseException e) {
      throw new UnclassifiedServerFailureException(502, "Directory store returned an error");
    }
  }
}
