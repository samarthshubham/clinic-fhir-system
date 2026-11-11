import { DataTable, Given, Then } from '@cucumber/cucumber';
import { apiClient } from '../support/api-client';
import { daysAgo } from '../support/date-helpers';
import { SeedWorld } from '../support/world';

// The payer HAPI loads its validation terminology on the first write after a start, which takes about 30s.
const PAYER_HAPI_WRITE = { timeout: 60_000 };

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
  'Praxis Health Plan has the patient as a member with the same coverage',
  PAYER_HAPI_WRITE,
  async function (this: SeedWorld) {
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
      status: coverage.status,
      periodStart: coverage.periodStart,
      periodEnd: coverage.periodEnd,
    });
    this.log(
      `Member ${member.id} with payer coverage ${result.id} (${coverage.memberId}, ${coverage.status})`,
    );
  },
);
