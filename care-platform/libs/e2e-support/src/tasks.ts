import { execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
// eslint-disable-next-line @nx/enforce-module-boundaries -- reuses the seed app's cascade delete instead of duplicating it
import {
  apiClient,
  type FhirResource,
  SEED_CLINIC,
} from '@seed-support/api-client';
// eslint-disable-next-line @nx/enforce-module-boundaries -- same relative dates as the seed
import { daysAgo } from '@seed-support/date-helpers';
// eslint-disable-next-line @nx/enforce-module-boundaries -- uses the seed's fixture root
import { repoPath } from '@seed-support/repo-paths';
// eslint-disable-next-line @nx/enforce-module-boundaries -- uses the seed's native terminology import
import {
  seedTerminology,
  waitForFragmentLookups,
  type SeedCodeSystem,
} from '@seed-support/payer-terminology';

const HAPI = 'http://localhost:8080/fhir';
const PAYER_HAPI = 'http://localhost:8081/fhir';
const HAPI_CONTAINER = 'clinic-hapi-fhir';
const TEST_FAMILY_PREFIX = 'E2E-';
const LOGIN_PATIENT_IDS = ['e2e-patient-1', 'e2e-patient-2'];

async function readCodeSystem(name: string): Promise<SeedCodeSystem> {
  const fixture = await readFile(
    repoPath('terminology/codesystems', name + '.json'),
    'utf8',
  );
  return JSON.parse(fixture) as SeedCodeSystem;
}

export interface TrackedData {
  patientIds?: string[];
  practitionerIds?: string[];
  payerResources?: string[];
}

export interface SweepOptions {
  payer?: boolean;
}

export interface TestPatient {
  id: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
}

export interface TestMember {
  lastName: string;
  memberId: string;
}

const PRAXIS = { organizationId: 'PHP01', name: 'Praxis Health Plan' };
const GOLD_PPO = {
  planId: 'praxis-gold-ppo',
  name: 'Praxis Gold PPO',
  planType: 'PPO',
};

const tracked = {
  patientIds: new Set<string>(),
  practitionerIds: new Set<string>(),
  payerResources: new Set<string>(),
};
let fhirStopped = false;

async function searchIds(
  resourceType: string,
  query: string,
  base = HAPI,
): Promise<string[]> {
  const response = await fetch(
    `${base}/${resourceType}?${query}&_count=100&_elements=id`,
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

// A resource written moments before (e.g. an alert Flag from the BP rest-hook) can make HAPI refuse the delete with 409.
async function retryOnConflict(
  remove: () => Promise<void>,
  attempts = 3,
): Promise<void> {
  for (let attempt = 1; ; attempt++) {
    try {
      await remove();
      return;
    } catch (e) {
      if (attempt >= attempts || !String(e).includes('[409]')) throw e;
      await new Promise((resolve) => setTimeout(resolve, 2000));
    }
  }
}

function deletePatient(id: string): Promise<void> {
  return retryOnConflict(() => apiClient.cascadeDeletePatient(id));
}

function deletePayerResource(reference: string): Promise<void> {
  return retryOnConflict(async () => {
    const response = await fetch(`${PAYER_HAPI}/${reference}?_cascade=delete`, {
      method: 'DELETE',
    });
    if (!response.ok && response.status !== 404) {
      throw new Error(`DELETE payer ${reference} failed [${response.status}]`);
    }
  });
}

async function deleteData(data: TrackedData): Promise<void> {
  for (const id of data.patientIds ?? []) await deletePatient(id);
  for (const id of data.practitionerIds ?? []) await deletePractitioner(id);
  for (const reference of data.payerResources ?? [])
    await deletePayerResource(reference);
}

async function sweepPayerTestData(): Promise<void> {
  const memberIds = await searchIds(
    'Patient',
    `family=${TEST_FAMILY_PREFIX}`,
    PAYER_HAPI,
  );
  await deleteData({ payerResources: memberIds.map((id) => `Patient/${id}`) });
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

async function startFhir(): Promise<void> {
  execFileSync('docker', ['start', HAPI_CONTAINER]);
  await waitForUrl(`${HAPI}/metadata`, 180_000);
  fhirStopped = false;
}

export const e2eTasks = {
  trackTestData(data: TrackedData): null {
    data.patientIds?.forEach((id) => tracked.patientIds.add(id));
    data.practitionerIds?.forEach((id) => tracked.practitionerIds.add(id));
    data.payerResources?.forEach((reference) =>
      tracked.payerResources.add(reference),
    );
    return null;
  },
  async upsertTestPatient(patient: TestPatient): Promise<null> {
    await deletePatient(patient.id);
    await apiClient.upsertPatient(patient);
    tracked.patientIds.add(patient.id);
    return null;
  },
  // Ensures Praxis Health Plan and its Gold PPO plan the same way the seed does, so the
  // member's Coverage has a payer and plan to point at even when the seed never ran.
  async createTestMember(member: TestMember): Promise<{ id: string }> {
    const organization = await apiClient.ensurePayerOrganization(PRAXIS);
    const plan = await apiClient.ensureInsurancePlan({
      ...GOLD_PPO,
      organizationId: organization.id,
      periodStart: daysAgo(730),
    });
    const patient = await apiClient.ensureMemberPatient(member.memberId, {
      id: member.memberId,
      name: [{ family: member.lastName, given: ['Test'] }],
      birthDate: '1980-05-15',
      gender: 'female',
    });
    tracked.payerResources.add(`Patient/${patient.id}`);
    await apiClient.ensureMemberCoverage({
      memberPatientId: patient.id,
      organizationId: organization.id,
      payerName: PRAXIS.name,
      memberId: member.memberId,
      plan: { id: plan.id, planId: GOLD_PPO.planId, name: GOLD_PPO.name },
      status: 'active',
      periodStart: daysAgo(30),
    });
    return { id: patient.id };
  },
  async ensurePasSubmissionFixtures(): Promise<null> {
    await apiClient.upsertOrganization(SEED_CLINIC.id, SEED_CLINIC.npi);
    const systems = await Promise.all(
      ['claim-type', 'CMSPlaceofServiceCodes', 'cpt', 'x12-1365'].map(
        readCodeSystem,
      ),
    );
    for (const system of systems) {
      await seedTerminology(PAYER_HAPI, system);
    }
    await waitForFragmentLookups(PAYER_HAPI, systems);
    return null;
  },
  async ensureMriDtrFixtures(): Promise<null> {
    await Promise.all(
      ['dtr-completionTime-codes', 'dtr-intendedaudience-codes'].map(
        async (name) => seedTerminology(PAYER_HAPI, await readCodeSystem(name)),
      ),
    );
    for (const name of [
      'valueset-lumbar-spine-indications',
      'library-mri-lumbar-spine',
      'questionnaire-mri-lumbar-spine',
    ]) {
      const fixture = await readFile(
        repoPath('fixtures/davinci/dtr', name + '.json'),
        'utf8',
      );
      await apiClient.putPayerResource(JSON.parse(fixture) as FhirResource);
    }
    return null;
  },
  async ensurePlanNetFixtures(): Promise<null> {
    const taxonomy = await readCodeSystem('nucc-provider-taxonomy');
    await seedTerminology(PAYER_HAPI, taxonomy);
    await waitForFragmentLookups(PAYER_HAPI, [taxonomy]);

    const organization = await apiClient.ensurePayerOrganization(PRAXIS);
    const plan = await apiClient.ensureInsurancePlan({
      ...GOLD_PPO,
      organizationId: organization.id,
      periodStart: daysAgo(730),
    });
    const { period } = await apiClient.findInsurancePlan(GOLD_PPO.name);

    // The Gold PPO fixture references the formulary even without the demo seed.
    const formulary = JSON.parse(
      await readFile(
        repoPath('fixtures/davinci/formulary/formulary.json'),
        'utf8',
      ),
    ) as FhirResource;
    const formularyUrl = `${PAYER_HAPI}/InsurancePlan/${formulary['id']}`;
    const existingFormulary = await fetch(formularyUrl, {
      headers: { Accept: 'application/fhir+json' },
    });
    if (existingFormulary.status === 404 || existingFormulary.status === 410) {
      await apiClient.putPayerResource(formulary);
    } else if (!existingFormulary.ok) {
      throw new Error(
        `GET ${formularyUrl} failed [${existingFormulary.status}]`,
      );
    }

    for (const name of [
      'organization-payer',
      'organization-clinic',
      'location-illinois',
      'organization-network',
      'endpoint',
      'location-clinic',
      'healthcare-service',
      'practitioner',
      'practitioner-clinician',
      'practitioner-role-aisha',
      'practitioner-role-anika',
      'insurance-plan',
    ]) {
      const fixture = await readFile(
        repoPath('fixtures/davinci/plan-net', name + '.json'),
        'utf8',
      );
      const resource = JSON.parse(fixture, (key, value: unknown) =>
        key === 'reference' && value === 'Organization/praxis-health-plan'
          ? `Organization/${organization.id}`
          : value,
      ) as FhirResource;
      if (name === 'organization-payer') resource['id'] = organization.id;
      if (name === 'insurance-plan') {
        resource['id'] = plan.id;
        resource['period'] = period;
      }
      await apiClient.putPayerResource(resource);
    }
    return null;
  },
  async stopFhir(): Promise<null> {
    fhirStopped = true;
    execFileSync('docker', ['stop', HAPI_CONTAINER]);
    return null;
  },
  async startFhir(): Promise<null> {
    await startFhir();
    return null;
  },
};

export async function sweepBeforeRun(
  options: SweepOptions = {},
): Promise<void> {
  await sweepTestData();
  if (options.payer) await sweepPayerTestData();
}

export async function cleanUpAfterSpec(failures: number): Promise<void> {
  if (fhirStopped) await startFhir();
  const data = {
    patientIds: [...tracked.patientIds],
    practitionerIds: [...tracked.practitionerIds],
    payerResources: [...tracked.payerResources],
  };
  tracked.patientIds.clear();
  tracked.practitionerIds.clear();
  tracked.payerResources.clear();
  if (failures > 0) {
    console.log(
      `Kept e2e data for inspection (spec had failures): ${JSON.stringify(data)}`,
    );
    return;
  }
  await deleteData(data);
}
