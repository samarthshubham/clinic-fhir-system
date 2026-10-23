import { After, Before } from '@badeball/cypress-cucumber-preprocessor';
import { resetContext } from '../support/context';

Before(() => {
  resetContext();
});

After({ tags: '@fhir-down' }, () => {
  cy.task('startFhir', null, { timeout: 200_000 });
});
