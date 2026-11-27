import { DataTable, Given, Then } from '@cucumber/cucumber';
import { readFileSync } from 'node:fs';
import { apiClient, FhirResource } from '../support/api-client';
import { repoPath } from '../support/repo-paths';

const RXNORM = 'http://www.nlm.nih.gov/research/umls/rxnorm';
const FORMULARY = 'http://hl7.org/fhir/us/davinci-drug-formulary';
const STRUCTURE = FORMULARY + '/StructureDefinition/usdf-';

interface FormularyItem extends FhirResource {
  extension: Array<{ url: string }>;
}

function fixture<T extends FhirResource>(name: string): T {
  return JSON.parse(
    readFileSync(
      repoPath('fixtures/davinci/formulary', name + '.json'),
      'utf8',
    ),
  ) as T;
}

Given('the payer HAPI has the Praxis formulary', async function () {
  const formulary = fixture('formulary');
  formulary['name'] = 'Praxis Health Plan Formulary';
  formulary['period'] = { start: '2026-01-01' };
  await apiClient.putPayerResource(formulary);
});

Given(
  'the Praxis formulary covers these RxNorm drugs:',
  { timeout: 120_000 },
  async function (dataTable: DataTable) {
    const drugTemplate = fixture('formulary-drug');
    const itemTemplate = fixture<FormularyItem>('formulary-item');
    const variableExtensions = new Set(
      [
        'AvailabilityPeriod',
        'DrugTierID',
        'PriorAuthorization',
        'QuantityLimit',
      ].map((name) => STRUCTURE + name + '-extension'),
    );
    const commonExtensions = itemTemplate.extension.filter(
      ({ url }) => !variableExtensions.has(url),
    );

    for (const row of dataTable.hashes()) {
      const id = 'praxis-rx-' + row['rxcui'];
      await apiClient.putPayerResource({
        ...drugTemplate,
        id,
        code: {
          coding: [
            { system: RXNORM, code: row['rxcui'], display: row['drug'] },
          ],
        },
        doseForm: { coding: [{ system: RXNORM, code: row['doseForm'] }] },
      });
      const limit = row['quantityLimit'];
      await apiClient.putPayerResource({
        ...itemTemplate,
        id: id + '-coverage',
        subject: { reference: 'MedicationKnowledge/' + id },
        extension: [
          ...commonExtensions,
          {
            url: STRUCTURE + 'AvailabilityPeriod-extension',
            valuePeriod: { start: '2026-01-01' },
          },
          {
            url: STRUCTURE + 'DrugTierID-extension',
            valueCodeableConcept: {
              coding: [
                {
                  system:
                    FORMULARY +
                    '/CodeSystem/usdf-DrugTierCS-TEMPORARY-TRIAL-USE',
                  code: row['tier'],
                },
              ],
            },
          },
          {
            url: STRUCTURE + 'PriorAuthorization-extension',
            valueBoolean: row['priorAuth'] === 'true',
          },
          { url: STRUCTURE + 'QuantityLimit-extension', valueBoolean: !!limit },
          ...(limit
            ? [
                {
                  url: STRUCTURE + 'QuantityLimitDetail-extension',
                  extension: [{ url: 'Description', valueString: limit }],
                },
              ]
            : []),
        ],
      });
    }
  },
);

Then(
  'the public formulary has these search matches:',
  async function (dataTable: DataTable) {
    for (const { type, query, id } of dataTable.hashes()) {
      const matches = await apiClient.publicFhirIds(type, query);
      if (!matches.includes(id)) {
        throw new Error(`${type}?${query} did not return ${id}`);
      }
    }
  },
);
