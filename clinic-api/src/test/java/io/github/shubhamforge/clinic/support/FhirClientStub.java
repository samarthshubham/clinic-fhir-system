package io.github.shubhamforge.clinic.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import ca.uhn.fhir.rest.gclient.IUpdate;
import ca.uhn.fhir.rest.gclient.IUpdateTyped;
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
  private final Map<Class<?>, Deque<Bundle>> searchResults = new HashMap<>();
  private final Map<Class<?>, Map<String, Resource>> stored = new HashMap<>();
  private final Map<String, RuntimeException> readFailures = new HashMap<>();
  private final Map<Class<?>, List<String>> pendingCriteria = new HashMap<>();
  private final Map<Class<?>, List<List<String>>> executedSearches = new HashMap<>();
  private final List<IBaseResource> created = new ArrayList<>();
  private final List<IBaseResource> updated = new ArrayList<>();
  private int nextId = 1;

  public FhirClientStub() {
    IUntypedQuery<?> untypedQuery = lenientMock(IUntypedQuery.class, Answers.RETURNS_DEFAULTS);
    lenient().doReturn(untypedQuery).when(client).search();
    lenient()
        .doAnswer(inv -> startQuery(inv.getArgument(0)))
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

    IUpdate update = lenientMock(IUpdate.class, Answers.RETURNS_DEFAULTS);
    lenient().doReturn(update).when(client).update();
    lenient()
        .doAnswer(inv -> updateTyped(inv.getArgument(0)))
        .when(update)
        .resource(any(IBaseResource.class));
  }

  public IGenericClient client() {
    return client;
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

  public void givenSearchResult(Class<? extends IBaseResource> type, Bundle... bundles) {
    searchResults.put(type, new ArrayDeque<>(Arrays.asList(bundles)));
  }

  public void givenStored(IBaseResource resource, String id) {
    resource.setId(id);
    store((Resource) resource, id);
  }

  public void givenReadFails(Class<? extends IBaseResource> type, String id, RuntimeException e) {
    readFailures.put(type.getSimpleName() + "/" + id, e);
  }

  public <T extends IBaseResource> T stored(Class<T> type, String id) {
    return type.cast(stored.getOrDefault(type, Map.of()).get(id));
  }

  public List<IBaseResource> created() {
    return created;
  }

  public List<IBaseResource> updated() {
    return updated;
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

  private IQuery<?> startQuery(Class<?> type) {
    pendingCriteria.remove(type);
    return queries.computeIfAbsent(type, this::newQuery);
  }

  private void store(Resource resource, String id) {
    Resource copy = resource.copy();
    copy.setId(id);
    stored.computeIfAbsent(resource.getClass(), k -> new HashMap<>()).put(id, copy);
  }

  private IQuery<?> newQuery(Class<?> type) {
    IQuery<?> query = lenientMock(IQuery.class, Answers.RETURNS_SELF);
    ISort<?> sort = lenientMock(ISort.class, Answers.RETURNS_DEFAULTS);
    lenient().doReturn(sort).when(query).sort();
    lenient()
        .doAnswer(inv -> record(type, "_sort=-" + inv.getArgument(0), query))
        .when(sort)
        .descending(anyString());
    lenient()
        .doAnswer(inv -> record(type, "_sort=" + inv.getArgument(0), query))
        .when(sort)
        .ascending(anyString());
    lenient()
        .doAnswer(inv -> record(type, "_count=" + inv.getArgument(0), query))
        .when(query)
        .count(anyInt());
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
    pendingCriteria.computeIfAbsent(type, k -> new ArrayList<>()).add(criterion);
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
              store((Resource) resource, id);
              created.add(resource);
              return new MethodOutcome(new IdType(resource.fhirType(), id));
            })
        .when(createTyped)
        .execute();
    return createTyped;
  }

  private IUpdateTyped updateTyped(IBaseResource resource) {
    IUpdateTyped updateTyped = lenientMock(IUpdateTyped.class, Answers.RETURNS_DEFAULTS);
    lenient()
        .doAnswer(
            inv -> {
              String id = resource.getIdElement().getIdPart();
              if (id == null) {
                throw new IllegalArgumentException("Cannot update a resource without an id");
              }
              store((Resource) resource, id);
              updated.add(resource);
              return new MethodOutcome(new IdType(resource.fhirType(), id));
            })
        .when(updateTyped)
        .execute();
    return updateTyped;
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
              RuntimeException failure = readFailures.get(type.getSimpleName() + "/" + id);
              if (failure != null) {
                throw failure;
              }
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
