package io.github.samarthshubham.payer.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.ICriterionInternal;
import ca.uhn.fhir.rest.gclient.IParam;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.IRead;
import ca.uhn.fhir.rest.gclient.IReadExecutable;
import ca.uhn.fhir.rest.gclient.IReadTyped;
import ca.uhn.fhir.rest.gclient.ISort;
import ca.uhn.fhir.rest.gclient.IUntypedQuery;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Resource;
import org.mockito.Answers;
import org.mockito.quality.Strictness;

/** A fake IGenericClient for search and read: records each search's criteria as "name=value". */
public final class FhirClientStub {

  private final IGenericClient client = lenientMock(IGenericClient.class, Answers.RETURNS_DEFAULTS);
  private final Map<Class<?>, Bundle> searchResults = new HashMap<>();
  private final Map<Class<?>, Map<String, Resource>> stored = new HashMap<>();
  private final Map<Class<?>, List<String>> pendingCriteria = new HashMap<>();
  private final Map<Class<?>, List<List<String>>> executedSearches = new HashMap<>();

  public FhirClientStub() {
    IUntypedQuery<?> untypedQuery = lenientMock(IUntypedQuery.class, Answers.RETURNS_DEFAULTS);
    lenient().doReturn(untypedQuery).when(client).search();
    lenient()
        .doAnswer(inv -> newQuery(inv.getArgument(0)))
        .when(untypedQuery)
        .forResource(any(Class.class));

    IRead read = lenientMock(IRead.class, Answers.RETURNS_DEFAULTS);
    lenient().doReturn(read).when(client).read();
    lenient().doAnswer(inv -> readTyped(inv.getArgument(0))).when(read).resource(any(Class.class));
  }

  public IGenericClient client() {
    return client;
  }

  public void givenSearchResult(Class<? extends IBaseResource> type, Bundle bundle) {
    searchResults.put(type, bundle);
  }

  public void givenStored(Resource resource, String id) {
    resource.setId(id);
    stored.computeIfAbsent(resource.getClass(), k -> new HashMap<>()).put(id, resource);
  }

  public int searchCount(Class<? extends IBaseResource> type) {
    return executedSearches.getOrDefault(type, List.of()).size();
  }

  public List<String> lastSearchCriteria(Class<? extends IBaseResource> type) {
    List<List<String>> searches = executedSearches.getOrDefault(type, List.of());
    if (searches.isEmpty()) {
      throw new AssertionError("No " + type.getSimpleName() + " search was executed");
    }
    return searches.get(searches.size() - 1);
  }

  public static Bundle bundleOf(Resource... resources) {
    Bundle bundle = new Bundle();
    for (Resource resource : resources) {
      bundle.addEntry().setResource(resource);
    }
    return bundle;
  }

  private IQuery<?> newQuery(Class<?> type) {
    pendingCriteria.put(type, new ArrayList<>());
    IQuery<?> query = lenientMock(IQuery.class, Answers.RETURNS_SELF);
    ISort<?> sort = lenientMock(ISort.class, Answers.RETURNS_DEFAULTS);
    lenient().doReturn(sort).when(query).sort();
    lenient()
        .doAnswer(
            inv -> record(type, "_sort=-" + ((IParam) inv.getArgument(0)).getParamName(), query))
        .when(sort)
        .descending(any(IParam.class));
    lenient()
        .doAnswer(inv -> record(type, "_count=" + inv.getArgument(0), query))
        .when(query)
        .count(anyInt());
    lenient()
        .doAnswer(
            inv -> record(type, "_revinclude=" + ((Include) inv.getArgument(0)).getValue(), query))
        .when(query)
        .revInclude(any(Include.class));
    lenient()
        .doAnswer(inv -> recordCriterion(type, inv.getArgument(0), query))
        .when(query)
        .where(any(ICriterion.class));
    lenient()
        .doAnswer(inv -> recordCriterion(type, inv.getArgument(0), query))
        .when(query)
        .and(any(ICriterion.class));
    lenient().doAnswer(inv -> executeSearch(type)).when(query).execute();
    return query;
  }

  private IQuery<?> recordCriterion(Class<?> type, ICriterion<?> criterion, IQuery<?> query) {
    ICriterionInternal internal = (ICriterionInternal) criterion;
    return record(
        type,
        internal.getParameterName() + "=" + internal.getParameterValue(FhirContext.forR4Cached()),
        query);
  }

  private IQuery<?> record(Class<?> type, String criterion, IQuery<?> query) {
    pendingCriteria.get(type).add(criterion);
    return query;
  }

  private Bundle executeSearch(Class<?> type) {
    executedSearches
        .computeIfAbsent(type, k -> new ArrayList<>())
        .add(List.copyOf(pendingCriteria.remove(type)));
    Bundle result = searchResults.get(type);
    return result == null ? new Bundle() : result.copy();
  }

  private IReadTyped<?> readTyped(Class<?> type) {
    IReadTyped<?> readTyped = lenientMock(IReadTyped.class, Answers.RETURNS_DEFAULTS);
    lenient()
        .doAnswer(inv -> readExecutable(type, inv.getArgument(0)))
        .when(readTyped)
        .withId(anyString());
    return readTyped;
  }

  private IReadExecutable<?> readExecutable(Class<?> type, String id) {
    IReadExecutable<?> readExecutable =
        lenientMock(IReadExecutable.class, Answers.RETURNS_DEFAULTS);
    lenient()
        .doAnswer(
            inv -> {
              Resource resource = stored.getOrDefault(type, Map.of()).get(id);
              if (resource == null) {
                throw new ResourceNotFoundException(new IdType(type.getSimpleName(), id));
              }
              return resource.copy();
            })
        .when(readExecutable)
        .execute();
    return readExecutable;
  }

  private static <T> T lenientMock(Class<T> type, Answers defaultAnswer) {
    return mock(type, withSettings().strictness(Strictness.LENIENT).defaultAnswer(defaultAnswer));
  }
}
