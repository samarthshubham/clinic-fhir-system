import { DataTable, Given, Then } from '@cucumber/cucumber';
import { readdirSync, readFileSync } from 'node:fs';
import { apiClient, FhirResource } from '../support/api-client';
import { daysAgo } from '../support/date-helpers';
import {
  seedTerminology,
  waitForFragmentLookups,
  type SeedCodeSystem,
} from '../support/payer-terminology';
import { repoPath } from '../support/repo-paths';
import { SeedWorld } from '../support/world';

// The payer HAPI loads its validation terminology on the first write after a start, which takes about 30s.
const PAYER_HAPI_WRITE = { timeout: 60_000 };
const CODE_SYSTEMS = repoPath('terminology/codesystems');

Given(
  'the seed has a payer-api system token',
  async function (this: SeedWorld) {
    this.payerApiToken = await apiClient.payerApiToken();
  },
);

Given(
  'payer organization {string} named {string} is in the payer HAPI',
  PAYER_HAPI_WRITE,
  async function (this: SeedWorld, organizationId: string, name: string) {
    const result = await apiClient.ensurePayerOrganization({
      organizationId,
      name,
    });
    this.payerOrganizationId = result.id;
    this.log(`Payer organization ${result.id} (${name})`);
  },
);

Given(
  'the payer offers these insurance plans:',
  PAYER_HAPI_WRITE,
  async function (this: SeedWorld, dataTable: DataTable) {
    const organizationId = this.payerOrganizationId;
    if (!organizationId) throw new Error('No payerOrganizationId');
    for (const row of dataTable.hashes()) {
      const result = await apiClient.ensureInsurancePlan({
        planId: row['planId'],
        name: row['name'],
        planType: row['planType'],
        organizationId,
        periodStart: daysAgo(Number(row['startedDaysAgo'])),
      });
      this.log(`Insurance plan ${result.id} (${row['name']})`);
    }
  },
);

Given('the provider patient {string}', function (this: SeedWorld, id: string) {
  this.currentPatientId = id;
});

Then(
  'Praxis Health Plan has the patient as a member:',
  PAYER_HAPI_WRITE,
  async function (this: SeedWorld, dataTable: DataTable) {
    const row = dataTable.rowsHash() as Record<string, string>;
    const ended = row['endedDaysAgo'];
    const coverage = this.currentCoverage;
    const organizationId = this.payerOrganizationId;
    if (!coverage) throw new Error('No currentCoverage');
    if (!organizationId) throw new Error('No payerOrganizationId');
    if (!coverage.planName) throw new Error('The coverage has no planName');

    const plan = await apiClient.findInsurancePlan(coverage.planName);
    const patient = await apiClient.getProviderPatient(coverage.patientId);
    const member = await apiClient.ensureMemberPatient(
      coverage.memberId,
      patient,
    );
    const result = await apiClient.ensureMemberCoverage({
      memberPatientId: member.id,
      organizationId,
      payerName: coverage.payerName,
      memberId: coverage.memberId,
      plan,
      status: row['status'],
      periodStart: coverage.periodStart,
      periodEnd: ended ? daysAgo(Number(ended)) : undefined,
    });
    this.log(
      `Member ${member.id} with payer coverage ${result.id} (${coverage.memberId}, ${row['status']})`,
    );
  },
);

Given(
  'the payer HAPI has these DTR fixtures:',
  PAYER_HAPI_WRITE,
  async function (this: SeedWorld, dataTable: DataTable) {
    for (const { file } of dataTable.hashes()) {
      const resource = JSON.parse(
        readFileSync(repoPath('fixtures/davinci/dtr', file), 'utf8'),
      ) as FhirResource;
      await apiClient.putPayerResource(resource);
      this.log(`${resource.resourceType}/${String(resource['id'])} (${file})`);
    }
  },
);

Given(
  'the payer HAPI has every CodeSystem in the terminology folder',
  { timeout: 40 * 60_000 },
  async function (this: SeedWorld) {
    const files = readdirSync(CODE_SYSTEMS)
      .filter((file) => file.endsWith('.json'))
      .sort();
    if (files.length === 0) {
      throw new Error(`No CodeSystems in ${CODE_SYSTEMS}`);
    }
    const base = process.env['PAYER_FHIR_URL'] ?? 'http://localhost:8081/fhir';
    const systems: SeedCodeSystem[] = [];
    for (const file of files) {
      const resource = JSON.parse(
        readFileSync(repoPath('terminology/codesystems', file), 'utf8'),
      ) as SeedCodeSystem;
      if (resource.resourceType !== 'CodeSystem') {
        throw new Error(
          `${file} is a ${resource.resourceType}, not a CodeSystem`,
        );
      }
      await seedTerminology(base, resource);
      systems.push(resource);
      this.log(
        `CodeSystem/${String(resource['id'])} (${String(resource['url'])})`,
      );
    }
    await waitForFragmentLookups(base, systems);
  },
);

Then(
  'payer-api returns these questionnaire packages:',
  async function (this: SeedWorld, dataTable: DataTable) {
    const token = this.payerApiToken;
    if (!token) throw new Error('No payerApiToken');
    for (const row of dataTable.hashes()) {
      const questionnaires = await apiClient.questionnairePackage(token, {
        system: row['system'],
        code: row['code'],
      });
      const expected = row['questionnaire'] ? [row['questionnaire']] : [];
      if (JSON.stringify(questionnaires) !== JSON.stringify(expected)) {
        throw new Error(
          `${row['order']}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(questionnaires)}`,
        );
      }
      this.log(
        `${row['order']}: ${questionnaires.join(', ') || 'no documentation'}`,
      );
    }
  },
);
