import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { cascadeDeletePatient, upsertPatient, execFileSync, payer } = vi.hoisted(
  () => ({
    cascadeDeletePatient: vi.fn<(id: string) => Promise<void>>(),
    upsertPatient: vi.fn<(patient: object) => Promise<void>>(),
    execFileSync: vi.fn(),
    payer: {
      ensurePayerOrganization: vi.fn(),
      ensureInsurancePlan: vi.fn(),
      ensureMemberPatient: vi.fn(),
      ensureMemberCoverage: vi.fn(),
    },
  }),
);

vi.mock('../../../apps/seed-demo-data/src/support/api-client', () => ({
  apiClient: { cascadeDeletePatient, upsertPatient, ...payer },
}));
vi.mock('node:child_process', () => ({ execFileSync }));

const HAPI = 'http://localhost:8080/fhir';
const PAYER_HAPI = 'http://localhost:8081/fhir';

const fetchMock = vi.fn<typeof fetch>();

function bundle(...ids: string[]): Response {
  return new Response(
    JSON.stringify({ entry: ids.map((id) => ({ resource: { id } })) }),
    { status: 200 },
  );
}

function status(code: number): Response {
  return new Response(null, { status: code });
}

function requests(): string[] {
  return fetchMock.mock.calls.map(
    ([url, init]) => `${init?.method ?? 'GET'} ${String(url)}`,
  );
}

async function loadTasks() {
  vi.resetModules();
  return import('../src/tasks');
}

beforeEach(() => {
  fetchMock.mockReset();
  cascadeDeletePatient.mockReset().mockResolvedValue();
  upsertPatient.mockReset().mockResolvedValue();
  execFileSync.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  vi.spyOn(console, 'log').mockImplementation(() => undefined);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('sweepBeforeRun', () => {
  it('removes login patients and E2E- patients and practitioners from the provider HAPI only', async () => {
    const { sweepBeforeRun } = await loadTasks();
    fetchMock
      .mockResolvedValueOnce(bundle('p1'))
      .mockResolvedValueOnce(bundle('pr1'))
      .mockResolvedValueOnce(status(204));

    await sweepBeforeRun();

    expect(cascadeDeletePatient.mock.calls.map(([id]) => id)).toEqual([
      'e2e-patient-1',
      'e2e-patient-2',
      'p1',
    ]);
    expect(requests()).toEqual([
      `GET ${HAPI}/Patient?family=E2E-&_count=100&_elements=id`,
      `GET ${HAPI}/Practitioner?family=E2E-&_count=100&_elements=id`,
      `DELETE ${HAPI}/Practitioner/pr1`,
    ]);
  });

  it('also cascade-deletes E2E- members on the payer HAPI when asked', async () => {
    const { sweepBeforeRun } = await loadTasks();
    fetchMock
      .mockResolvedValueOnce(bundle())
      .mockResolvedValueOnce(bundle())
      .mockResolvedValueOnce(bundle('m1', 'm2'))
      .mockResolvedValueOnce(status(200))
      .mockResolvedValueOnce(status(404));

    await sweepBeforeRun({ payer: true });

    expect(requests().slice(2)).toEqual([
      `GET ${PAYER_HAPI}/Patient?family=E2E-&_count=100&_elements=id`,
      `DELETE ${PAYER_HAPI}/Patient/m1?_cascade=delete`,
      `DELETE ${PAYER_HAPI}/Patient/m2?_cascade=delete`,
    ]);
  });

  it('fails when a search fails', async () => {
    const { sweepBeforeRun } = await loadTasks();
    fetchMock.mockResolvedValueOnce(status(500));

    await expect(sweepBeforeRun()).rejects.toThrow(
      'GET Patient?family=E2E- failed [500]',
    );
  });

  it('fails when a practitioner delete fails', async () => {
    const { sweepBeforeRun } = await loadTasks();
    fetchMock
      .mockResolvedValueOnce(bundle())
      .mockResolvedValueOnce(bundle('pr1'))
      .mockResolvedValueOnce(status(500));

    await expect(sweepBeforeRun()).rejects.toThrow(
      'DELETE Practitioner/pr1 failed [500]',
    );
  });
});

describe('cleanUpAfterSpec', () => {
  it('deletes tracked provider and payer data after a passing spec', async () => {
    const { cleanUpAfterSpec, e2eTasks } = await loadTasks();
    fetchMock.mockResolvedValue(status(200));
    e2eTasks.trackTestData({
      patientIds: ['p1'],
      practitionerIds: ['pr1'],
      payerResources: ['Patient/m1', 'Claim/c1'],
    });

    await cleanUpAfterSpec(0);

    expect(cascadeDeletePatient).toHaveBeenCalledWith('p1');
    expect(requests()).toEqual([
      `DELETE ${HAPI}/Practitioner/pr1`,
      `DELETE ${PAYER_HAPI}/Patient/m1?_cascade=delete`,
      `DELETE ${PAYER_HAPI}/Claim/c1?_cascade=delete`,
    ]);

    fetchMock.mockClear();
    await cleanUpAfterSpec(0);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('keeps tracked data after a failing spec', async () => {
    const { cleanUpAfterSpec, e2eTasks } = await loadTasks();
    e2eTasks.trackTestData({ payerResources: ['Patient/m1'] });

    await cleanUpAfterSpec(1);

    expect(fetchMock).not.toHaveBeenCalled();
    expect(console.log).toHaveBeenCalledWith(
      expect.stringContaining('"payerResources":["Patient/m1"]'),
    );
  });

  it('retries a payer delete that HAPI refuses with 409', async () => {
    vi.useFakeTimers();
    const { cleanUpAfterSpec, e2eTasks } = await loadTasks();
    fetchMock
      .mockResolvedValueOnce(status(409))
      .mockResolvedValueOnce(status(200));
    e2eTasks.trackTestData({ payerResources: ['Patient/m1'] });

    const cleanup = cleanUpAfterSpec(0);
    await vi.advanceTimersByTimeAsync(2000);
    await cleanup;

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('gives up on a payer delete after three conflicts', async () => {
    vi.useFakeTimers();
    const { cleanUpAfterSpec, e2eTasks } = await loadTasks();
    fetchMock.mockResolvedValue(status(409));
    e2eTasks.trackTestData({ payerResources: ['Patient/m1'] });

    const cleanup = expect(cleanUpAfterSpec(0)).rejects.toThrow(
      'DELETE payer Patient/m1 failed [409]',
    );
    await vi.advanceTimersByTimeAsync(4000);
    await cleanup;

    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('fails on a payer delete error other than a conflict', async () => {
    const { cleanUpAfterSpec, e2eTasks } = await loadTasks();
    fetchMock.mockResolvedValue(status(500));
    e2eTasks.trackTestData({ payerResources: ['Claim/c1'] });

    await expect(cleanUpAfterSpec(0)).rejects.toThrow(
      'DELETE payer Claim/c1 failed [500]',
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('restarts the provider HAPI first when a scenario stopped it', async () => {
    const { cleanUpAfterSpec, e2eTasks } = await loadTasks();
    fetchMock.mockResolvedValue(status(200));

    await e2eTasks.stopFhir();
    await cleanUpAfterSpec(0);

    expect(execFileSync.mock.calls).toEqual([
      ['docker', ['stop', 'clinic-hapi-fhir']],
      ['docker', ['start', 'clinic-hapi-fhir']],
    ]);
    expect(requests()).toEqual([`GET ${HAPI}/metadata`]);
  });
});

describe('e2eTasks', () => {
  it('replaces a test patient and tracks it for cleanup', async () => {
    const { cleanUpAfterSpec, e2eTasks } = await loadTasks();
    const patient = {
      id: 'e2e-patient-1',
      firstName: 'Test',
      lastName: 'E2E-Patient',
      dateOfBirth: '1980-01-01',
      gender: 'female',
    };

    await e2eTasks.upsertTestPatient(patient);
    await cleanUpAfterSpec(0);

    expect(upsertPatient).toHaveBeenCalledWith(patient);
    expect(cascadeDeletePatient.mock.calls).toEqual([
      ['e2e-patient-1'],
      ['e2e-patient-1'],
    ]);
  });

  it('creates a payer member with Gold PPO coverage and tracks it for cleanup', async () => {
    const { cleanUpAfterSpec, e2eTasks } = await loadTasks();
    payer.ensurePayerOrganization.mockResolvedValue({ id: 'org1' });
    payer.ensureInsurancePlan.mockResolvedValue({ id: 'plan1' });
    payer.ensureMemberPatient.mockResolvedValue({ id: 'm1' });
    payer.ensureMemberCoverage.mockResolvedValue({ id: 'cov1' });
    fetchMock.mockResolvedValue(status(200));

    const member = await e2eTasks.createTestMember({
      lastName: 'E2E-member-x',
      memberId: 'PHP-91234',
    });
    await cleanUpAfterSpec(0);

    expect(member).toEqual({ id: 'm1' });
    expect(payer.ensurePayerOrganization).toHaveBeenCalledWith({
      organizationId: 'PHP01',
      name: 'Praxis Health Plan',
    });
    expect(payer.ensureInsurancePlan).toHaveBeenCalledWith(
      expect.objectContaining({
        planId: 'praxis-gold-ppo',
        organizationId: 'org1',
      }),
    );
    expect(payer.ensureMemberPatient).toHaveBeenCalledWith(
      'PHP-91234',
      expect.objectContaining({
        name: [{ family: 'E2E-member-x', given: ['Test'] }],
      }),
    );
    expect(payer.ensureMemberCoverage).toHaveBeenCalledWith(
      expect.objectContaining({
        memberPatientId: 'm1',
        organizationId: 'org1',
        memberId: 'PHP-91234',
        status: 'active',
        plan: {
          id: 'plan1',
          planId: 'praxis-gold-ppo',
          name: 'Praxis Gold PPO',
        },
      }),
    );
    expect(requests()).toEqual([
      `DELETE ${PAYER_HAPI}/Patient/m1?_cascade=delete`,
    ]);
  });

  it('starts the provider HAPI and waits for it', async () => {
    vi.useFakeTimers();
    const { e2eTasks } = await loadTasks();
    fetchMock
      .mockRejectedValueOnce(new TypeError('fetch failed'))
      .mockResolvedValueOnce(status(200));

    const started = e2eTasks.startFhir();
    await vi.advanceTimersByTimeAsync(2000);

    await expect(started).resolves.toBeNull();
    expect(execFileSync).toHaveBeenCalledWith('docker', [
      'start',
      'clinic-hapi-fhir',
    ]);
  });
});

describe('waitForUrls', () => {
  it('fails when a URL does not become healthy in time', async () => {
    vi.useFakeTimers();
    const { waitForUrls } = await loadTasks();
    fetchMock.mockResolvedValue(status(503));

    const waiting = expect(
      waitForUrls(['http://localhost:4200'], 4000),
    ).rejects.toThrow('http://localhost:4200 did not become healthy within 4s');
    await vi.advanceTimersByTimeAsync(4000);
    await waiting;
  });
});
