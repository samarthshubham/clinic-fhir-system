// eslint-disable-next-line @nx/enforce-module-boundaries -- use the seed's payer FHIR writer
import { apiClient } from '@seed-support/api-client';
import { e2eTasks } from '@care-platform/e2e-support';

const PAS = 'http://hl7.org/fhir/us/davinci-pas/StructureDefinition/';
const description = 'Cardiac stress test';

export async function createPendedRequest(memberId: string): Promise<null> {
  const { id: patientId } = await e2eTasks.createTestMember({
    memberId,
    lastName: `E2E-Queue-${memberId}`,
  });
  const organization = await apiClient.ensurePayerOrganization({
    organizationId: 'PHP01',
    name: 'Praxis Health Plan',
  });
  const claimId = `e2e-claim-${memberId}`;
  const responseId = `e2e-response-${memberId}`;
  const now = Date.now();
  const created = new Date(now - 8 * 86_400_000).toISOString();
  const deadline = new Date(now - 86_400_000).toISOString();
  const type = {
    coding: [
      {
        system: 'http://terminology.hl7.org/CodeSystem/claim-type',
        code: 'professional',
      },
    ],
  };
  const patient = { reference: `Patient/${patientId}` };
  const insurer = { reference: `Organization/${organization.id}` };
  const common = {
    status: 'active',
    type,
    use: 'preauthorization',
    patient,
    insurer,
  };

  await apiClient.putPayerResource({
    ...common,
    resourceType: 'Claim',
    id: claimId,
    created,
    provider: insurer,
    priority: {
      coding: [
        {
          system: 'http://terminology.hl7.org/CodeSystem/processpriority',
          code: 'normal',
        },
      ],
    },
    insurance: [
      {
        sequence: 1,
        focal: true,
        coverage: {
          type: 'Coverage',
          identifier: {
            system: 'http://praxishealthplan.example/fhir/identifier/member-id',
            value: memberId,
          },
        },
      },
    ],
    extension: [
      {
        url: 'http://praxishealthplan.example/fhir/StructureDefinition/decision-deadline',
        valueDateTime: deadline,
      },
    ],
    item: [{ sequence: 1, productOrService: { text: description } }],
  });
  await apiClient.putPayerResource({
    ...common,
    resourceType: 'ClaimResponse',
    id: responseId,
    created: new Date(now).toISOString(),
    requestor: insurer,
    request: { reference: `Claim/${claimId}` },
    outcome: 'complete',
    preAuthRef: memberId,
    item: [
      {
        itemSequence: 1,
        adjudication: [
          {
            category: {
              coding: [
                {
                  system: 'http://terminology.hl7.org/CodeSystem/adjudication',
                  code: 'submitted',
                },
              ],
            },
            extension: [
              {
                url: PAS + 'extension-reviewAction',
                extension: [
                  {
                    url: PAS + 'extension-reviewActionCode',
                    valueCodeableConcept: {
                      coding: [
                        {
                          system: 'https://codesystem.x12.org/005010/306',
                          code: 'A4',
                        },
                      ],
                    },
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
  });
  return null;
}
