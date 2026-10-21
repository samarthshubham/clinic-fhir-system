package io.github.shubhamforge.clinic.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICreate;
import ca.uhn.fhir.rest.gclient.ICreateTyped;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.ICriterionInternal;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.IRead;
import ca.uhn.fhir.rest.gclient.IReadExecutable;
import ca.uhn.fhir.rest.gclient.IReadTyped;
import ca.uhn.fhir.rest.gclient.ISort;
import ca.uhn.fhir.rest.gclient.IUntypedQuery;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Resource;
import org.mockito.Answers;
import org.mockito.quality.Strictness;

public final class FhirClientStub {

  private final IGenericClient client = lenientMock(IGenericClient.class, Answers.RETURNS_DEFAULTS);
  private final Map<Class<?>, IQuery<?>> queries = new HashMap<>();
  private final Map<Class<?>, ISort<?>> sorts = new HashMap<>();
  private final Map<Class<?>, Deque<Bundle>> searchResults = new HashMap<>();
  private final Map<Class<?>, Map<String, IBaseResource>> stored = new HashMap<>();
  private final Map<Class<?>, List<String>> pendingCriteria = new HashMap<>();
  private final Map<Class<?>, List<List<String>>> executedSearches = new HashMap<>();
  private final List<IBaseResource> created = new ArrayList<>();
  private int nextId = 1;

  public FhirClientStub() {
    IUntypedQuery<?> untypedQuery = lenientMock(IUntypedQuery.class, Answers.RETURNS_DEFAULTS);
    lenient().doReturn(untypedQuery).when(client).search();
    lenient()
        .doAnswer(inv -> query(inv.getArgument(0)))
        .when(untypedQuery)
        .forResource(any(Class.class));

    ICreate create = lenientMock(ICreate.class, Answers.RETURNS_DEFAULTS);
    lenient().doReturn(create).when(client).create();
    lenient()
        .doAnswer(inv -> createTyped(inv.getArgument(0)))
        .when(create)
        .resource(any(IBaseResource.class));

    IRead read = lenientMock(IRead.class, Answers.RETURNS_DEFAULTS);
    lenient().doReturn(read).when(client).read();
    lenient().doAnswer(inv -> readTyped(inv.getArgument(0))).when(read).resource(any(Class.class));
  }

  public IGenericClient client() {
    return client;
  }

  public IQuery<?> query(Class<? extends IBaseResource> type) {
    return queries.computeIfAbsent(type, this::newQuery);
  }

  public ISort<?> sort(Class<? extends IBaseResource> type) {
    query(type);
    return sorts.get(type);
  }

  public List<String> lastSearchCriteria(Class<? extends IBaseResource> type) {
    List<List<String>> searches = executedSearches.getOrDefault(type, List.of());
    if (searches.isEmpty()) {
      throw new AssertionError("No " + type.getSimpleName() + " search was executed");
    }
    return searches.get(searches.size() - 1);
  }

  public void givenSearchResult(Class<? extends IBaseResource> type, Bundle... bundles) {
    searchResults.put(type, new ArrayDeque<>(Arrays.asList(bundles)));
  }

  public void givenStored(IBaseResource resource, String id) {
    resource.setId(id);
    stored.computeIfAbsent(resource.getClass(), k -> new HashMap<>()).put(id, resource);
  }

  public List<IBaseResource> created() {
    return created;
  }

  public static Observation quantityObservation(double value) {
    return new Observation().setValue(new Quantity().setValue(value));
  }

  public static Bundle bundleOf(Resource... resources) {
    Bundle bundle = new Bundle();
    for (Resource resource : resources) {
      bundle.addEntry().setResource(resource);
    }
    return bundle;
  }

  private IQuery<?> newQuery(Class<?> type) {
    IQuery<?> query = lenientMock(IQuery.class, Answers.RETURNS_SELF);
    ISort<?> sort = lenientMock(ISort.class, Answers.RETURNS_DEFAULTS);
    lenient().doReturn(sort).when(query).sort();
    lenient().doReturn(query).when(sort).descending(anyString());
    lenient().doReturn(query).when(sort).ascending(anyString());
    lenient()
        .doAnswer(inv -> recordCriterion(type, inv.getArgument(0), query))
        .when(query)
        .where(any(ICriterion.class));
    lenient()
        .doAnswer(inv -> recordCriterion(type, inv.getArgument(0), query))
        .when(query)
        .and(any(ICriterion.class));
    lenient().doAnswer(inv -> executeSearch(type)).when(query).execute();
    sorts.put(type, sort);
    return query;
  }

  private IQuery<?> recordCriterion(Class<?> type, ICriterion<?> criterion, IQuery<?> query) {
    ICriterionInternal internal = (ICriterionInternal) criterion;
    pendingCriteria
        .computeIfAbsent(type, k -> new ArrayList<>())
        .add(
            internal.getParameterName()
                + "="
                + internal.getParameterValue(FhirContext.forR4Cached()));
    return query;
  }

  private Bundle executeSearch(Class<?> type) {
    List<String> criteria = pendingCriteria.remove(type);
    executedSearches
        .computeIfAbsent(type, k -> new ArrayList<>())
        .add(criteria == null ? List.of() : List.copyOf(criteria));
    return nextSearchResult(type);
  }

  private Bundle nextSearchResult(Class<?> type) {
    Deque<Bundle> results = searchResults.get(type);
    if (results == null || results.isEmpty()) {
      return new Bundle();
    }
    return results.size() > 1 ? results.poll() : results.peek();
  }

  private ICreateTyped createTyped(IBaseResource resource) {
    ICreateTyped createTyped = lenientMock(ICreateTyped.class, Answers.RETURNS_DEFAULTS);
    lenient()
        .doAnswer(
            inv -> {
              String id = String.valueOf(nextId++);
              givenStored(resource, id);
              created.add(resource);
              return new MethodOutcome(new IdType(resource.fhirType(), id));
            })
        .when(createTyped)
        .execute();
    return createTyped;
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
              IBaseResource resource = stored.getOrDefault(type, Map.of()).get(id);
              if (resource == null) {
                throw new ResourceNotFoundException(type.getSimpleName() + "/" + id);
              }
              return resource;
            })
        .when(readExecutable)
        .execute();
    return readExecutable;
  }

  private static <T> T lenientMock(Class<T> type, Answers defaultAnswer) {
    return mock(type, withSettings().strictness(Strictness.LENIENT).defaultAnswer(defaultAnswer));
  }
}
