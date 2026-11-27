import { Then, When } from '@cucumber/cucumber';
import { readFileSync } from 'node:fs';
import { apiClient, FhirIdentifier, FhirResource } from '../support/api-client';
import { daysAgo } from '../support/date-helpers';
import { repoPath } from '../support/repo-paths';
import { SeedWorld } from '../support/world';

const PAS = 'http://hl7.org/fhir/us/davinci-pas/StructureDefinition/';

interface Coding {
  system: string;
  code: string;
}

interface Extension {
  url: string;
  extension?: Extension[];
  valueCodeableConcept?: { coding?: Coding[] };
}

interface PasDecision extends FhirResource {
  item?: { adjudication: { extension?: Extension[] }[] }[];
}

When(
  'the seed submits the PAS fixture {string} {int} days ago',
  { timeout: 90_000 },
  async function (this: SeedWorld, fixture: string, age: number) {
    if (!this.payerApiToken) throw new Error('Missing payer API token');
    const submission = JSON.parse(
      readFileSync(repoPath('fixtures/davinci/pas', fixture), 'utf8'),
    ) as FhirResource & {
      entry: { resource: FhirResource & { identifier?: FhirIdentifier[] } }[];
    };
    const claim = submission.entry[0].resource;
    const identifier = claim.identifier?.[0];
    if (!identifier)
      throw new Error('PAS fixture is missing the Claim identifier');
    const existing = await apiClient.findPriorAuthDecision(identifier);
    if (existing) {
      this.pasDecision = existing;
      return;
    }

    const created = `${daysAgo(age)}T00:00:00Z`;
    claim['created'] = created;
    submission['timestamp'] = created;
    const response = await apiClient.submitPriorAuth(
      this.payerApiToken,
      submission,
    );
    this.pasDecision = (
      response as FhirResource & { entry: { resource: PasDecision }[] }
    ).entry[0].resource;
  },
);

Then(
  'the authorization response has a pended review action',
  function (this: SeedWorld) {
    const decision = this.pasDecision as PasDecision | null;
    const pended =
      decision?.resourceType === 'ClaimResponse' &&
      decision.item?.some((item) =>
        item.adjudication.some((adjudication) =>
          adjudication.extension?.some(
            (action) =>
              action.url === PAS + 'extension-reviewAction' &&
              action.extension?.some(
                (code) =>
                  code.url === PAS + 'extension-reviewActionCode' &&
                  code.valueCodeableConcept?.coding?.some(
                    (coding) =>
                      coding.system ===
                        'https://codesystem.x12.org/005010/306' &&
                      coding.code === 'A4',
                  ),
              ),
          ),
        ),
      );
    if (!pended) throw new Error('Expected a pended PAS ClaimResponse');
  },
);
