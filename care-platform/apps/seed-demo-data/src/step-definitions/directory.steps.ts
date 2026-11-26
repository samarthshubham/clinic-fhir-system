import { DataTable, Given, Then } from '@cucumber/cucumber';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { apiClient, FhirResource } from '../support/api-client';
import { SeedWorld } from '../support/world';

const PLAN_NET_FIXTURES = resolve(
  __dirname,
  '../../../../../fixtures/davinci/plan-net',
);

Given(
  'the payer HAPI has these Plan-Net fixtures:',
  { timeout: 60_000 },
  async function (this: SeedWorld, dataTable: DataTable) {
    const payerId = this.payerOrganizationId;
    if (!payerId) throw new Error('No payerOrganizationId');

    for (const { file } of dataTable.hashes()) {
      const resource = JSON.parse(
        readFileSync(resolve(PLAN_NET_FIXTURES, file), 'utf8'),
        (key, value: unknown) =>
          key === 'reference' && value === 'Organization/praxis-health-plan'
            ? `Organization/${payerId}`
            : value,
      ) as FhirResource;
      if (resource['id'] === 'praxis-health-plan') {
        resource['id'] = payerId;
      }
      if (resource.resourceType === 'InsurancePlan') {
        const plan = await apiClient.findInsurancePlan(
          String(resource['name']),
        );
        resource['id'] = plan.id;
        resource['period'] = plan.period;
      }
      await apiClient.putPayerResource(resource);
      this.log(`${resource.resourceType}/${String(resource['id'])} (${file})`);
    }
  },
);

Then(
  'the public directory has these search matches:',
  async function (this: SeedWorld, dataTable: DataTable) {
    for (const { type, query, id } of dataTable.hashes()) {
      const matches = await apiClient.publicDirectoryIds(type, query);
      if (!matches.includes(id)) {
        throw new Error(`${type}?${query} did not return ${id}`);
      }
      this.log(`${type}/${id} is in the public directory`);
    }
  },
);
