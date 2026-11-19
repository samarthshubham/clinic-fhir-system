import { Given } from '@cucumber/cucumber';
import { apiClient } from '../support/api-client';
import { SeedWorld } from '../support/world';

Given(
  'practitioner {string} is provisioned',
  async function (this: SeedWorld, id: string) {
    await apiClient.upsertPractitioner(id);
    this.log(`Provisioned practitioner: ${id}`);
  },
);

Given(
  'staff practitioner {string} {string} {string} with title {string} is provisioned',
  async function (
    this: SeedWorld,
    username: string,
    firstName: string,
    lastName: string,
    title: string,
  ) {
    await apiClient.upsertStaffPractitioner({
      username,
      firstName,
      lastName,
      title,
    });
    this.log(`Provisioned staff practitioner: user-${username}`);
  },
);

Given(
  'organization {string} is provisioned',
  async function (this: SeedWorld, id: string) {
    await apiClient.upsertOrganization(id);
    this.log(`Provisioned organization: ${id}`);
  },
);
