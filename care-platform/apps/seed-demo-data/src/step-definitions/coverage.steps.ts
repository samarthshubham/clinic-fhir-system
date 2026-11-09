import { DataTable, Given } from '@cucumber/cucumber';
import { CreateCoverageBody, apiClient } from '../support/api-client';
import { daysAgo } from '../support/date-helpers';
import { SeedWorld } from '../support/world';

const PRAXIS_HEALTH_PLAN = {
  payerId: 'praxis-health-plan',
  payerName: 'Praxis Health Plan',
};

Given(
  'the patient is covered by Praxis Health Plan:',
  async function (this: SeedWorld, dataTable: DataTable) {
    if (!this.currentPatientId) throw new Error('No currentPatientId');
    const row = dataTable.rowsHash() as Record<string, string>;
    const ended = row['endedDaysAgo'];
    const coverage: CreateCoverageBody = {
      patientId: this.currentPatientId,
      ...PRAXIS_HEALTH_PLAN,
      memberId: row['memberId'],
      planName: row['planName'],
      status: row['status'],
      periodStart: daysAgo(Number(row['startedDaysAgo'])),
      ...(ended && { periodEnd: daysAgo(Number(ended)) }),
    };
    const result = await apiClient.ensureCoverage(coverage);
    this.currentCoverage = coverage;
    this.log(`Coverage ${result.id} (${row['memberId']}, ${row['status']})`);
  },
);
