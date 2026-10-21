import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../src/support/api-client';

const HAPI = 'http://localhost:8080/fhir';
const SPRING = 'http://localhost:9090/api';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function textResponse(text: string, status: number): Response {
  return new Response(text, { status });
}

function bundle(...ids: string[]) {
  return {
    resourceType: 'Bundle',
    entry: ids.map((id) => ({ resource: { id } })),
  };
}

const fetchMock = vi.fn<typeof fetch>();

function calls(): Array<{ url: string; init?: RequestInit }> {
  return fetchMock.mock.calls.map(([url, init]) => ({
    url: String(url),
    init,
  }));
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('retry on stale pooled connections', () => {
  it('retries a TypeError and returns the next successful response', async () => {
    fetchMock
      .mockRejectedValueOnce(new TypeError('fetch failed'))
      .mockResolvedValueOnce(jsonResponse({ id: 'g1' }));

    const goal = await apiClient.createGoal({
      patientId: 'p1',
      description: 'Lower BP',
      status: 'active',
    });

    expect(goal).toEqual({ id: 'g1' });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('gives up after two retries and rethrows the last TypeError', async () => {
    const lastError = new TypeError('fetch failed 3');
    fetchMock
      .mockRejectedValueOnce(new TypeError('fetch failed 1'))
      .mockRejectedValueOnce(new TypeError('fetch failed 2'))
      .mockRejectedValueOnce(lastError)
      .mockResolvedValue(jsonResponse({}));

    await expect(apiClient.upsertPractitioner('pr1')).rejects.toBe(lastError);
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('succeeds on the final allowed attempt', async () => {
    fetchMock
      .mockRejectedValueOnce(new TypeError('fetch failed'))
      .mockRejectedValueOnce(new TypeError('fetch failed'))
      .mockResolvedValueOnce(jsonResponse({}));

    await expect(apiClient.upsertOrganization('org1')).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('does not retry errors other than TypeError', async () => {
    const abort = new Error('aborted');
    fetchMock.mockRejectedValueOnce(abort).mockResolvedValue(jsonResponse({}));

    await expect(apiClient.upsertPractitioner('pr1')).rejects.toBe(abort);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('does not retry HTTP error responses', async () => {
    fetchMock.mockResolvedValue(textResponse('boom', 500));

    await expect(apiClient.upsertPractitioner('pr1')).rejects.toThrow('[500]');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe('POST to the Spring API', () => {
  const cases = [
    ['createEncounter', 'encounters'],
    ['recordVitals', 'vitals'],
    ['createCondition', 'conditions'],
    ['createMedication', 'medications'],
    ['createAppointment', 'appointments'],
    ['createServiceRequest', 'service-requests'],
    ['createDiagnosticReport', 'diagnostic-reports'],
    ['createCarePlan', 'care-plans'],
    ['createGoal', 'goals'],
  ] as const;

  it.each(cases)('%s posts JSON to /api/%s', async (method, path) => {
    const body = { patientId: 'p1', marker: method };
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: 'new-id' }));

    const result = await (
      apiClient[method] as (b: unknown) => Promise<unknown>
    ).call(apiClient, body);

    expect(result).toEqual({ id: 'new-id' });
    expect(calls()).toEqual([
      {
        url: `${SPRING}/${path}`,
        init: {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body),
        },
      },
    ]);
  });

  it('includes status and a truncated response body in the error', async () => {
    fetchMock.mockResolvedValueOnce(textResponse('x'.repeat(500), 400));

    const error = await apiClient
      .createEncounter({
        patientId: 'p1',
        visitDate: '2026-01-01',
        status: 'finished',
      })
      .catch((e: Error) => e);

    expect(error).toBeInstanceOf(Error);
    expect((error as Error).message).toBe(
      `POST ${SPRING}/encounters failed [400]: ${'x'.repeat(300)}`,
    );
  });
});

describe('PUT to HAPI FHIR', () => {
  it('upserts a patient with telecom when phone and email are present', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}));

    await apiClient.upsertPatient({
      id: 'p1',
      firstName: 'Ada',
      lastName: 'Lovelace',
      dateOfBirth: '1815-12-10',
      gender: 'female',
      phone: '555-0100',
      email: 'ada@example.com',
    });

    const [{ url, init }] = calls();
    expect(url).toBe(`${HAPI}/Patient/p1`);
    expect(init?.method).toBe('PUT');
    expect(init?.headers).toEqual({ 'Content-Type': 'application/fhir+json' });
    expect(JSON.parse(init?.body as string)).toEqual({
      resourceType: 'Patient',
      id: 'p1',
      active: true,
      name: [{ family: 'Lovelace', given: ['Ada'] }],
      birthDate: '1815-12-10',
      gender: 'female',
      telecom: [
        { system: 'phone', value: '555-0100' },
        { system: 'email', value: 'ada@example.com' },
      ],
    });
  });

  it('omits telecom when the patient has no contact details', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}));

    await apiClient.upsertPatient({
      id: 'p2',
      firstName: 'Alan',
      lastName: 'Turing',
      dateOfBirth: '1912-06-23',
      gender: 'male',
    });

    const body = JSON.parse(calls()[0].init?.body as string);
    expect(body).not.toHaveProperty('telecom');
  });

  it('keeps only the contact details that are present', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}));

    await apiClient.upsertPatient({
      id: 'p3',
      firstName: 'Grace',
      lastName: 'Hopper',
      dateOfBirth: '1906-12-09',
      gender: 'female',
      email: 'grace@example.com',
    });

    const body = JSON.parse(calls()[0].init?.body as string);
    expect(body.telecom).toEqual([
      { system: 'email', value: 'grace@example.com' },
    ]);
  });

  it('upserts the practitioner and organization under the given ids', async () => {
    fetchMock.mockImplementation(async () => jsonResponse({}));

    await apiClient.upsertPractitioner('pr1');
    await apiClient.upsertOrganization('org1');

    const [practitioner, organization] = calls();
    expect(practitioner.url).toBe(`${HAPI}/Practitioner/pr1`);
    expect(JSON.parse(practitioner.init?.body as string)).toMatchObject({
      resourceType: 'Practitioner',
      id: 'pr1',
    });
    expect(organization.url).toBe(`${HAPI}/Organization/org1`);
    expect(JSON.parse(organization.init?.body as string)).toMatchObject({
      resourceType: 'Organization',
      id: 'org1',
    });
  });

  it('includes status and a truncated response body in the error', async () => {
    fetchMock.mockResolvedValueOnce(textResponse('y'.repeat(400), 422));

    await expect(apiClient.upsertOrganization('org1')).rejects.toThrow(
      `PUT ${HAPI}/Organization/org1 failed [422]: ${'y'.repeat(300)}`,
    );
  });
});

describe('cascadeDeletePatient', () => {
  const searchOrder = [
    'CarePlan',
    'Goal',
    'DiagnosticReport',
    'ServiceRequest',
    'Appointment',
    'MedicationStatement',
    'Condition',
    'Observation',
    'Encounter',
  ];

  function routeFetch(
    found: Record<string, unknown>,
    deleteStatus: (url: string) => number = () => 204,
  ) {
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input);
      if (init?.method === 'DELETE')
        return new Response(null, { status: deleteStatus(url) });
      const type = url.slice(`${HAPI}/`.length).split('?')[0];
      return jsonResponse(found[type] ?? { resourceType: 'Bundle' });
    });
  }

  it('searches each type with _count=100, id-only elements and the patient filter', async () => {
    routeFetch({});

    await apiClient.cascadeDeletePatient('p1');

    const searches = calls().filter(({ init }) => init?.method !== 'DELETE');
    expect(searches.map(({ url }) => url)).toEqual(
      searchOrder.map(
        (type) => `${HAPI}/${type}?_count=100&_elements=id&patient=p1`,
      ),
    );
    for (const { init } of searches) {
      expect(init?.headers).toEqual({ Accept: 'application/fhir+json' });
    }
  });

  it('deletes referencing resources before the resources they reference', async () => {
    routeFetch({
      CarePlan: bundle('cp1'),
      Goal: bundle('g1', 'g2'),
      DiagnosticReport: bundle('dr1'),
      ServiceRequest: bundle('sr1'),
      Appointment: bundle('ap1'),
      MedicationStatement: bundle('ms1'),
      Condition: bundle('c1'),
      Observation: bundle('o1', 'o2'),
      Encounter: bundle('e1'),
    });

    await apiClient.cascadeDeletePatient('p1');

    const deletes = calls()
      .filter(({ init }) => init?.method === 'DELETE')
      .map(({ url }) => url.slice(`${HAPI}/`.length));
    expect(deletes).toEqual([
      'CarePlan/cp1',
      'Goal/g1',
      'Goal/g2',
      'DiagnosticReport/dr1',
      'ServiceRequest/sr1',
      'Appointment/ap1',
      'MedicationStatement/ms1',
      'Condition/c1',
      'Observation/o1',
      'Observation/o2',
      'Encounter/e1',
      'Patient/p1',
    ]);
  });

  it('skips entries without an id', async () => {
    routeFetch({
      Observation: {
        entry: [{ resource: { id: 'o1' } }, { resource: { id: '' } }],
      },
    });

    await apiClient.cascadeDeletePatient('p1');

    const deletes = calls()
      .filter(({ init }) => init?.method === 'DELETE')
      .map(({ url }) => url.slice(`${HAPI}/`.length));
    expect(deletes).toEqual(['Observation/o1', 'Patient/p1']);
  });

  it('treats an already deleted resource as success', async () => {
    routeFetch({ Goal: bundle('g1') }, () => 404);

    await expect(apiClient.cascadeDeletePatient('p1')).resolves.toBeUndefined();
  });

  it('fails when a delete is rejected', async () => {
    routeFetch({ Goal: bundle('g1') }, (url) =>
      url.endsWith('/Goal/g1') ? 409 : 204,
    );

    await expect(apiClient.cascadeDeletePatient('p1')).rejects.toThrow(
      'DELETE /fhir/Goal/g1 failed [409]',
    );
    expect(calls().some(({ url }) => url.includes('/Encounter'))).toBe(false);
  });

  it('fails when the patient delete is rejected', async () => {
    routeFetch({}, (url) => (url.endsWith('/Patient/p1') ? 500 : 204));

    await expect(apiClient.cascadeDeletePatient('p1')).rejects.toThrow(
      'DELETE /fhir/Patient/p1 failed [500]',
    );
  });

  it('fails when a search is rejected', async () => {
    fetchMock.mockImplementation(async (input) =>
      String(input).includes('/Goal?')
        ? textResponse('', 503)
        : jsonResponse({ resourceType: 'Bundle' }),
    );

    await expect(apiClient.cascadeDeletePatient('p1')).rejects.toThrow(
      'GET /fhir/Goal failed [503]',
    );
    expect(calls().every(({ init }) => init?.method !== 'DELETE')).toBe(true);
  });
});
