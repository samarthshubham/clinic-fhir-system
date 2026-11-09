import { DataTable, Given } from '@cucumber/cucumber';
import { PatientFields, apiClient } from '../support/api-client';
import { SeedWorld } from '../support/world';

Given(
  'a patient exists with the following details:',
  async function (this: SeedWorld, dataTable: DataTable) {
    const row = dataTable.rowsHash() as Record<string, string>;
    const fields: PatientFields = {
      id: row['id'],
      firstName: row['firstName'],
      lastName: row['lastName'],
      dateOfBirth: row['dateOfBirth'],
      gender: row['gender'],
      phone: row['phone'],
      email: row['email'],
      mrn: row['mrn'],
    };

    await apiClient.cascadeDeletePatient(fields.id);
    await apiClient.upsertPatient(fields);

    this.currentPatientId = fields.id;
    this.log(
      `Provisioned patient: ${fields.id} (${fields.firstName} ${fields.lastName})`,
    );
  },
);
