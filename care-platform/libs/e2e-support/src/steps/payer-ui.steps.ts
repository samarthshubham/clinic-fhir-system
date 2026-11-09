import { Then, When } from '@badeball/cypress-cucumber-preprocessor';
import { PASSWORDS } from '../support/api';

const payerUrl = (path: string): string => `${Cypress.env('payerUrl')}${path}`;

When(
  'the reviewer signs in to the payer app as {string}',
  (username: string) => {
    cy.visit(payerUrl('/login'));
    cy.get('input[formcontrolname="username"]').clear().type(username);
    cy.get('input[formcontrolname="password"]')
      .clear()
      .type(PASSWORDS[username], { log: false });
    cy.contains('button', 'Sign in').click();
  },
);

Then('the payer app shows the review queue', () => {
  cy.location('pathname').should('eq', '/queue');
  cy.contains('h1', 'Review queue').should('be.visible');
  cy.get('nav[aria-label="Main"] a[aria-current="page"]').should(
    'contain.text',
    'Queue',
  );
});

Then('the review queue is empty', () => {
  cy.contains(
    '[role="status"]',
    'No prior authorization requests to review',
  ).should('be.visible');
});
