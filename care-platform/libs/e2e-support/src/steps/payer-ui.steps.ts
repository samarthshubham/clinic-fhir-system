import { Given, Then, When } from '@badeball/cypress-cucumber-preprocessor';
import { PASSWORDS } from '../support/api';
import { ctx, resolveId, runId } from '../support/context';

const payerUrl = (path: string): string => `${Cypress.env('payerUrl')}${path}`;
const memberIdKey = (alias: string): string => `${alias}:memberId`;
const membersTableRows = 'table[aria-label="Members"] tr.mat-mdc-row';

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

Given('a Praxis Gold PPO member {string}', (alias: string) => {
  const lastName = `E2E-${alias}-${runId}`;
  const memberId = `PHP-9${String(Math.floor(Math.random() * 10_000)).padStart(4, '0')}`;
  cy.task<{ id: string }>('createTestMember', { lastName, memberId }).then(
    ({ id }) => {
      ctx.ids[alias] = id;
      ctx.names[alias] = lastName;
      ctx.names[memberIdKey(alias)] = memberId;
    },
  );
});

When('the reviewer opens the members page', () => {
  cy.get('nav[aria-label="Main"]').contains('a', 'Members').click();
  cy.location('pathname').should('eq', '/members');
  cy.contains('h1', 'Members').should('be.visible');
});

When(
  'the reviewer searches members by the name of {string}',
  (alias: string) => {
    cy.get('input[formcontrolname="memberId"]').clear();
    cy.get('input[formcontrolname="name"]').clear().type(ctx.names[alias]);
  },
);

When(
  'the reviewer searches members by the member ID of {string}',
  (alias: string) => {
    cy.get('input[formcontrolname="name"]').clear();
    cy.get('input[formcontrolname="memberId"]')
      .clear()
      .type(ctx.names[memberIdKey(alias)]);
  },
);

Then('the members table lists only {string}', (alias: string) => {
  cy.get(membersTableRows)
    .should('have.length', 1)
    .first()
    .should('contain.text', ctx.names[alias])
    .and('contain.text', ctx.names[memberIdKey(alias)])
    .and('contain.text', 'Praxis Gold PPO')
    .and('contain.text', 'Active');
});

When('the reviewer opens the member {string}', (alias: string) => {
  cy.get(membersTableRows).contains('a', ctx.names[alias]).click();
  cy.location('pathname').should('eq', `/members/${resolveId(alias)}`);
});

Then(
  'the member page shows the Praxis Gold PPO coverage of {string}',
  (alias: string) => {
    cy.contains('h1', ctx.names[alias]).should('be.visible');
    cy.contains('dd.cp-font-mono', ctx.names[memberIdKey(alias)]).should(
      'be.visible',
    );
    cy.contains('mat-card', 'Praxis Gold PPO')
      .should('contain.text', 'Praxis Health Plan')
      .and('contain.text', 'Active');
  },
);

Then('the member has no prior authorizations', () => {
  cy.contains('[role="status"]', 'No prior authorizations').should(
    'be.visible',
  );
});
