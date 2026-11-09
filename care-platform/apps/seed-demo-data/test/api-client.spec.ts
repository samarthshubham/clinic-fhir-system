import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '../src/support/api-client';

const HAPI = 'http://localhost:8080/fhir';
const SPRING = 'http://localhost:9090/api';
const PAYER_HAPI = 'http://localhost:8081/fhir';
const PAYER_API = 'http://localhost:9095';
const US_CORE = 'http://hl7.org/fhir/us/core/StructureDefinition';
const MEMBER_ID_SYSTEM =
  'http://praxishealthplan.example/fhir/identifier/member-id';

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

describe('login', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('logs in as the default clinician account', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ accessToken: 't0' }));

    await apiClient.login();

    expect(calls()).toEqual([
      {
        url: `${SPRING}/auth/login`,
        init: {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            username: 'clinician',
            password: 'Clinician#2026',
          }),
        },
      },
    ]);
  });

  it('takes credentials from SEED_USERNAME and SEED_PASSWORD', async () => {
    vi.stubEnv('SEED_USERNAME', 'admin');
    vi.stubEnv('SEED_PASSWORD', 'Admin#2026');
    fetchMock.mockResolvedValueOnce(jsonResponse({ accessToken: 't0' }));

    await apiClient.login();

    expect(JSON.parse(String(calls()[0].init?.body))).toEqual({
      username: 'admin',
      password: 'Admin#2026',
    });
  });

  it('fails with the status and response body when rejected', async () => {
    fetchMock.mockResolvedValueOnce(
      textResponse(
        '{"status":401,"message":"Invalid username or password"}',
        401,
      ),
    );

    await expect(apiClient.login('clinician', 'wrong')).rejects.toThrow(
      'Login as clinician failed [401]: {"status":401,"message":"Invalid username or password"}',
    );
  });
});

describe('POST to the Spring API', () => {
  beforeEach(async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ accessToken: 't1' }));
    await apiClient.login();
    fetchMock.mockClear();
  });

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
          headers: {
            'Content-Type': 'application/json',
            Authorization: 'Bearer t1',
          },
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

describe('ensureCoverage', () => {
  const body = {
    patientId: 'p1',
    payerId: 'praxis-health-plan',
    payerName: 'Praxis Health Plan',
    memberId: 'PHP-10001',
    status: 'active',
    periodStart: '2026-01-01',
  };
  const memberSearch = `${HAPI}/Coverage?_count=100&_elements=id&identifier=${encodeURIComponent(
    'http://praxishealthplan.example/fhir/identifier/member-id|PHP-10001',
  )}`;

  it('keeps the coverage that already has the member id', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(bundle('cov1')));

    await expect(apiClient.ensureCoverage(body)).resolves.toEqual({
      id: 'cov1',
    });
    expect(calls().map(({ url }) => url)).toEqual([memberSearch]);
  });

  it('creates the coverage through the Spring API when the member id is new', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ resourceType: 'Bundle' }))
      .mockResolvedValueOnce(jsonResponse({ id: 'cov2' }));

    await expect(apiClient.ensureCoverage(body)).resolves.toEqual({
      id: 'cov2',
    });
    const [, create] = calls();
    expect(create.url).toBe(`${SPRING}/coverages`);
    expect(create.init?.method).toBe('POST');
    expect(JSON.parse(create.init?.body as string)).toEqual(body);
  });
});

describe('payer-api token', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('asks for a client_credentials token as the dev clinic-api client', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ access_token: 'sys1' }));

    await expect(apiClient.payerApiToken()).resolves.toBe('sys1');

    const basic = Buffer.from(
      'clinic-api:dev-only-clinic-api-client-secret',
    ).toString('base64');
    expect(calls()).toEqual([
      {
        url: `${PAYER_API}/oauth/token`,
        init: {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            Authorization: `Basic ${basic}`,
          },
          body: 'grant_type=client_credentials',
        },
      },
    ]);
  });

  it('takes the client from PAYER_CLIENT_ID and PAYER_CLIENT_SECRET', async () => {
    vi.stubEnv('PAYER_CLIENT_ID', 'other-client');
    vi.stubEnv('PAYER_CLIENT_SECRET', 'other-secret');
    fetchMock.mockResolvedValueOnce(jsonResponse({ access_token: 'sys2' }));

    await apiClient.payerApiToken();

    const headers = calls()[0].init?.headers as Record<string, string>;
    expect(headers['Authorization']).toBe(
      `Basic ${Buffer.from('other-client:other-secret').toString('base64')}`,
    );
  });

  it('fails with the OAuth error when the client is rejected', async () => {
    fetchMock.mockResolvedValueOnce(
      textResponse('{"error":"invalid_client"}', 401),
    );

    await expect(
      apiClient.payerApiToken('clinic-api', 'wrong'),
    ).rejects.toThrow(
      'Token request for clinic-api failed [401]: {"error":"invalid_client"}',
    );
  });
});

describe('payer HAPI conditional creates', () => {
  const plan = { id: 'ip1', planId: 'gold-ppo', name: 'Praxis Gold PPO' };

  function sent(): {
    url: string;
    headers: Record<string, string>;
    body: Record<string, unknown>;
  } {
    const [{ url, init }] = calls();
    return {
      url,
      headers: init?.headers as Record<string, string>,
      body: JSON.parse(init?.body as string),
    };
  }

  function ifNoneExist(system: string, value: string): string {
    return `identifier=${encodeURIComponent(`${system}|${value}`)}`;
  }

  it('creates the payer organization unless its identifier exists', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: 'org1', extra: 1 }));

    await expect(
      apiClient.ensurePayerOrganization({
        organizationId: 'praxis-health-plan',
        name: 'Praxis Health Plan',
      }),
    ).resolves.toEqual({ id: 'org1' });

    const { url, headers, body } = sent();
    expect(url).toBe(`${PAYER_HAPI}/Organization`);
    expect(headers).toEqual({
      'Content-Type': 'application/fhir+json',
      Accept: 'application/fhir+json',
      'If-None-Exist': ifNoneExist(
        'http://praxishealthplan.example/fhir/identifier/organization',
        'praxis-health-plan',
      ),
    });
    expect(body).toMatchObject({
      resourceType: 'Organization',
      meta: { profile: [`${US_CORE}/us-core-organization`] },
      active: true,
      name: 'Praxis Health Plan',
      type: [{ coding: [{ code: 'pay' }] }],
    });
  });

  it('creates an insurance plan owned and administered by the payer', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: 'ip1' }));

    await apiClient.ensureInsurancePlan({
      planId: 'gold-ppo',
      name: 'Praxis Gold PPO',
      planType: 'PPO',
      organizationId: 'org1',
      periodStart: '2024-11-09',
    });

    const { url, headers, body } = sent();
    expect(url).toBe(`${PAYER_HAPI}/InsurancePlan`);
    expect(headers['If-None-Exist']).toBe(
      ifNoneExist(
        'http://praxishealthplan.example/fhir/identifier/insurance-plan',
        'gold-ppo',
      ),
    );
    expect(body).toMatchObject({
      resourceType: 'InsurancePlan',
      status: 'active',
      name: 'Praxis Gold PPO',
      period: { start: '2024-11-09' },
      ownedBy: { reference: 'Organization/org1' },
      administeredBy: { reference: 'Organization/org1' },
      plan: [{ type: { text: 'PPO' } }],
    });
  });

  it('creates the member patient from the provider demographics under the member id', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: 'm1' }));

    await apiClient.ensureMemberPatient('PHP-10001', {
      id: 'seed-priya-nair',
      name: [{ family: 'Nair', given: ['Priya'] }],
      birthDate: '1992-07-28',
      gender: 'female',
      telecom: [{ system: 'phone', value: '5551002002' }],
    });

    const { url, headers, body } = sent();
    expect(url).toBe(`${PAYER_HAPI}/Patient`);
    expect(headers['If-None-Exist']).toBe(
      ifNoneExist(MEMBER_ID_SYSTEM, 'PHP-10001'),
    );
    expect(body).toEqual({
      resourceType: 'Patient',
      meta: { profile: [`${US_CORE}/us-core-patient`] },
      identifier: [
        {
          type: {
            coding: [
              {
                system: 'http://terminology.hl7.org/CodeSystem/v2-0203',
                code: 'MB',
                display: 'Member Number',
              },
            ],
          },
          system: MEMBER_ID_SYSTEM,
          value: 'PHP-10001',
        },
      ],
      active: true,
      name: [{ family: 'Nair', given: ['Priya'] }],
      birthDate: '1992-07-28',
      gender: 'female',
      telecom: [{ system: 'phone', value: '5551002002' }],
    });
  });

  it('leaves telecom out of the member patient when the provider has none', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: 'm2' }));

    await apiClient.ensureMemberPatient('PHP-10002', { id: 'p2' });

    expect(sent().body).not.toHaveProperty('telecom');
  });

  it('creates the member coverage with the shared member id and the plan class', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: 'c1' }));

    await apiClient.ensureMemberCoverage({
      memberPatientId: 'm1',
      organizationId: 'org1',
      payerName: 'Praxis Health Plan',
      memberId: 'PHP-10008',
      plan,
      status: 'cancelled',
      periodStart: '2025-10-05',
      periodEnd: '2026-10-10',
    });

    const { url, headers, body } = sent();
    expect(url).toBe(`${PAYER_HAPI}/Coverage`);
    expect(headers['If-None-Exist']).toBe(
      ifNoneExist(MEMBER_ID_SYSTEM, 'PHP-10008'),
    );
    expect(body).toMatchObject({
      resourceType: 'Coverage',
      meta: { profile: [`${US_CORE}/us-core-coverage`] },
      identifier: [{ system: MEMBER_ID_SYSTEM, value: 'PHP-10008' }],
      status: 'cancelled',
      subscriberId: 'PHP-10008',
      beneficiary: { reference: 'Patient/m1' },
      relationship: { coding: [{ code: 'self' }] },
      period: { start: '2025-10-05', end: '2026-10-10' },
      payor: [
        { reference: 'Organization/org1', display: 'Praxis Health Plan' },
      ],
      class: [
        {
          type: { coding: [{ code: 'plan' }] },
          value: 'gold-ppo',
          name: 'Praxis Gold PPO',
        },
      ],
    });
  });

  it('leaves the period open when the coverage has not ended', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: 'c2' }));

    await apiClient.ensureMemberCoverage({
      memberPatientId: 'm1',
      organizationId: 'org1',
      payerName: 'Praxis Health Plan',
      memberId: 'PHP-10001',
      plan,
      status: 'active',
      periodStart: '2025-11-09',
    });

    expect(sent().body['period']).toEqual({ start: '2025-11-09' });
  });

  it('includes status and a truncated response body when the payer HAPI rejects it', async () => {
    fetchMock.mockResolvedValueOnce(textResponse('z'.repeat(400), 422));

    await expect(
      apiClient.ensureMemberPatient('PHP-10001', { id: 'p1' }),
    ).rejects.toThrow(
      `POST ${PAYER_HAPI}/Patient failed [422]: ${'z'.repeat(300)}`,
    );
  });
});

describe('payer HAPI and provider HAPI reads', () => {
  it('finds an insurance plan by exact name and returns its plan id', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        entry: [
          { resource: { id: 'other', identifier: [] } },
          {
            resource: {
              id: 'ip1',
              identifier: [
                { system: 'urn:other', value: 'x' },
                {
                  system:
                    'http://praxishealthplan.example/fhir/identifier/insurance-plan',
                  value: 'gold-ppo',
                },
              ],
            },
          },
        ],
      }),
    );

    await expect(
      apiClient.findInsurancePlan('Praxis Gold PPO'),
    ).resolves.toEqual({
      id: 'ip1',
      planId: 'gold-ppo',
      name: 'Praxis Gold PPO',
    });
    expect(calls()[0].url).toBe(
      `${PAYER_HAPI}/InsurancePlan?name:exact=${encodeURIComponent('Praxis Gold PPO')}`,
    );
  });

  it('fails when the plan has not been seeded', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ resourceType: 'Bundle' }));

    await expect(
      apiClient.findInsurancePlan('Praxis Gold PPO'),
    ).rejects.toThrow(
      'Insurance plan "Praxis Gold PPO" is not in the payer HAPI; run payer-plans.feature first',
    );
  });

  it('fails when the plan search is rejected', async () => {
    fetchMock.mockResolvedValueOnce(textResponse('', 500));

    await expect(
      apiClient.findInsurancePlan('Praxis Gold PPO'),
    ).rejects.toThrow('GET /fhir/InsurancePlan failed [500]');
  });

  it('reads the provider patient from the provider HAPI', async () => {
    const patient = { id: 'seed-priya-nair', gender: 'female' };
    fetchMock.mockResolvedValueOnce(jsonResponse(patient));

    await expect(
      apiClient.getProviderPatient('seed-priya-nair'),
    ).resolves.toEqual(patient);
    expect(calls()[0].url).toBe(`${HAPI}/Patient/seed-priya-nair`);
  });

  it('fails when the provider patient is missing', async () => {
    fetchMock.mockResolvedValueOnce(textResponse('', 404));

    await expect(apiClient.getProviderPatient('nobody')).rejects.toThrow(
      'GET /fhir/Patient/nobody failed [404]',
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

  it('adds the MRN identifier when the patient has one', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}));

    await apiClient.upsertPatient({
      id: 'p4',
      firstName: 'Katherine',
      lastName: 'Johnson',
      dateOfBirth: '1918-08-26',
      gender: 'female',
      mrn: 'PX-10000009',
    });

    const body = JSON.parse(calls()[0].init?.body as string);
    expect(body.identifier).toEqual([
      {
        use: 'usual',
        type: {
          coding: [
            {
              system: 'http://terminology.hl7.org/CodeSystem/v2-0203',
              code: 'MR',
            },
          ],
        },
        system: 'http://praxis.clinic/fhir/identifier/mrn',
        value: 'PX-10000009',
      },
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
    'Coverage',
    'Flag',
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
      Coverage: bundle('cov1'),
      Flag: bundle('f1'),
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
      'Coverage/cov1',
      'Flag/f1',
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
