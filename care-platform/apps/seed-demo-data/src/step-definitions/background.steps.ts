import { type DataTable, Given } from '@cucumber/cucumber';
import { apiClient, SEED_CLINIC } from '../support/api-client';
import { SeedWorld } from '../support/world';

Given(
  'practitioner {string} is provisioned',
  async function (this: SeedWorld, id: string) {
    await apiClient.upsertPractitioner(id);
    this.log(`Provisioned practitioner: ${id}`);
  },
);

Given(
  'the following staff practitioners are provisioned:',
  async function (this: SeedWorld, table: DataTable) {
    for (const {
      username,
      firstName,
      lastName,
      title,
      npi,
    } of table.hashes()) {
      await apiClient.upsertStaffPractitioner({
        username,
        firstName,
        lastName,
        title,
        npi,
      });
      this.log(`Provisioned staff practitioner: user-${username}`);
    }
  },
);

Given(
  'organization {string} is provisioned',
  async function (this: SeedWorld, id: string) {
    await apiClient.upsertOrganization(
      id,
      id === SEED_CLINIC.id ? SEED_CLINIC : undefined,
    );
    this.log(`Provisioned organization: ${id}`);
  },
);
