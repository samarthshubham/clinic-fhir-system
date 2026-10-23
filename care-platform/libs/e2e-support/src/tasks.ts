import { execFileSync } from 'node:child_process';
import { apiClient } from '../../../apps/seed-demo-data/src/support/api-client';

const HAPI = 'http://localhost:8080/fhir';
const HAPI_CONTAINER = 'clinic-hapi-fhir';
const TEST_FAMILY_PREFIX = 'E2E';
const LOGIN_PATIENT_IDS = ['e2e-patient-1', 'e2e-patient-2'];

export interface TrackedData {
  patientIds?: string[];
  practitionerIds?: string[];
}

export interface TestPatient {
  id: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
}

const tracked = {
  patientIds: new Set<string>(),
  practitionerIds: new Set<string>(),
};

async function searchIds(
  resourceType: string,
  query: string,
): Promise<string[]> {
  const response = await fetch(
    `${HAPI}/${resourceType}?${query}&_count=100&_elements=id`,
    {
      headers: { Accept: 'application/fhir+json' },
    },
  );
  if (!response.ok)
    throw new Error(`GET ${resourceType}?${query} failed [${response.status}]`);
  const bundle = (await response.json()) as {
    entry?: Array<{ resource: { id: string } }>;
  };
  return bundle.entry?.map((e) => e.resource.id) ?? [];
}

async function deletePractitioner(id: string): Promise<void> {
  const response = await fetch(`${HAPI}/Practitioner/${id}`, {
    method: 'DELETE',
  });
  if (!response.ok && response.status !== 404) {
    throw new Error(`DELETE Practitioner/${id} failed [${response.status}]`);
  }
}

async function deleteData(data: TrackedData): Promise<void> {
  for (const id of data.patientIds ?? [])
    await apiClient.cascadeDeletePatient(id);
  for (const id of data.practitionerIds ?? []) await deletePractitioner(id);
}

async function sweepTestData(): Promise<void> {
  const patientIds = [
    ...LOGIN_PATIENT_IDS,
    ...(await searchIds('Patient', `family=${TEST_FAMILY_PREFIX}`)),
  ];
  const practitionerIds = await searchIds(
    'Practitioner',
    `family=${TEST_FAMILY_PREFIX}`,
  );
  await deleteData({ patientIds, practitionerIds });
}

async function waitForUrl(url: string, timeoutMs: number): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      if ((await fetch(url)).ok) return;
    } catch {
      // not listening yet
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error(`${url} did not become healthy within ${timeoutMs / 1000}s`);
}

export async function waitForUrls(
  urls: string[],
  timeoutMs = 180_000,
): Promise<void> {
  for (const url of urls) await waitForUrl(url, timeoutMs);
}

export const e2eTasks = {
  trackTestData(data: TrackedData): null {
    data.patientIds?.forEach((id) => tracked.patientIds.add(id));
    data.practitionerIds?.forEach((id) => tracked.practitionerIds.add(id));
    return null;
  },
  async upsertTestPatient(patient: TestPatient): Promise<null> {
    await apiClient.cascadeDeletePatient(patient.id);
    await apiClient.upsertPatient(patient);
    tracked.patientIds.add(patient.id);
    return null;
  },
  async stopFhir(): Promise<null> {
    execFileSync('docker', ['stop', HAPI_CONTAINER]);
    return null;
  },
  async startFhir(): Promise<null> {
    execFileSync('docker', ['start', HAPI_CONTAINER]);
    await waitForUrl(`${HAPI}/metadata`, 180_000);
    return null;
  },
};

export async function sweepBeforeRun(): Promise<void> {
  await sweepTestData();
}

export async function cleanUpAfterSpec(failures: number): Promise<void> {
  const data = {
    patientIds: [...tracked.patientIds],
    practitionerIds: [...tracked.practitionerIds],
  };
  tracked.patientIds.clear();
  tracked.practitionerIds.clear();
  if (failures > 0) {
    console.log(
      `Kept e2e data for inspection (spec had failures): ${JSON.stringify(data)}`,
    );
    return;
  }
  await deleteData(data);
}
