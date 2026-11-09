import { Given, Then, When } from '@badeball/cypress-cucumber-preprocessor';
import { PASSWORDS } from '../support/api';
import { ctx } from '../support/context';

const clinicianUrl = (path: string): string =>
  `${Cypress.env('clinicianUrl')}${path}`;

function signIn(username: string): void {
  cy.get('input[formcontrolname="username"]').clear().type(username);
  cy.get('input[formcontrolname="password"]')
    .clear()
    .type(PASSWORDS[username], { log: false });
  cy.contains('button', 'Sign in').click();
}

Given('I sign in to the clinician app as {string}', (username: string) => {
  cy.visit(clinicianUrl('/login'));
  signIn(username);
  cy.location('pathname').should('eq', '/patients');
});

Given('I try to sign in to the clinician app with a wrong password', () => {
  cy.visit(clinicianUrl('/login'));
  cy.get('input[formcontrolname="username"]').type('clinician');
  cy.get('input[formcontrolname="password"]').type('not-the-password', {
    log: false,
  });
  cy.contains('button', 'Sign in').click();
});

When('I open the clinician app without signing in', () => {
  cy.visit(clinicianUrl('/patients'));
});

When('I search the roster for {string}', (term: string) => {
  cy.get('input[type="search"]').type(term);
});

When('I search the roster for the test patient', () => {
  cy.get('input[type="search"]').type(ctx.names['patient']);
});

When("I open the test patient's chart from the roster", () => {
  cy.contains('tr', ctx.names['patient']).click();
  cy.location('pathname').should('eq', `/patients/${ctx.ids['patient']}`);
});

When("I open the test patient's chart", () => {
  cy.visit(clinicianUrl(`/patients/${ctx.ids['patient']}`));
});

When('I open the clinician inbox', () => {
  cy.contains('a', 'Inbox').click();
  cy.location('pathname').should('eq', '/inbox');
});

When('I acknowledge the inbox alert for the test patient', () => {
  cy.contains('li', ctx.names['patient'])
    .contains('button', 'Acknowledge')
    .click();
});

Then('I am on the patient roster', () => {
  cy.location('pathname').should('eq', '/patients');
  cy.get('table[aria-label="Patient roster"]').should('exist');
});

Then('I am on the sign-in page', () => {
  cy.location('pathname').should('eq', '/login');
});

Then('I see the sign-in error {string}', (message: string) => {
  cy.get('[role="alert"]').should('contain.text', message);
});

Then('the roster lists the test patient', () => {
  cy.contains('tr', ctx.names['patient']).should('be.visible');
});

Then('the roster shows no matches', () => {
  cy.contains('No patients match your search', { timeout: 15_000 }).should(
    'exist',
  );
});

Then('the chart header shows the test patient', () => {
  cy.contains(ctx.names['patient'], { timeout: 15_000 }).should('be.visible');
});

Then(
  'the chart shows a {word} alert {string}',
  (severity: string, text: string) => {
    cy.contains(`.alert--${severity} .alert-msg`, text, {
      timeout: 15_000,
    }).should('exist');
  },
);

Then('the chart lists the active condition {string}', (display: string) => {
  cy.contains('.cond-name', display, { timeout: 15_000 }).should('exist');
});

Then(
  'the chart shows the latest blood pressure {string}',
  (reading: string) => {
    cy.contains('cp-vital-cell', 'BP', { timeout: 15_000 }).should(
      'contain.text',
      reading,
    );
  },
);

Then('the chart timeline shows {string}', (text: string) => {
  cy.contains(text, { timeout: 15_000 }).should('exist');
});

Then('the inbox shows a high BP alert for the test patient', () => {
  cy.contains('li', ctx.names['patient'], { timeout: 90_000 }).should(
    'contain.text',
    'High BP',
  );
});

Then('the inbox no longer shows the test patient', () => {
  cy.contains('li', ctx.names['patient']).should('not.exist');
});

const coverageCard = () =>
  cy.get('section[aria-labelledby="cp-coverage-card-title"]', {
    timeout: 15_000,
  });

When(
  'I add coverage with payer {string} \\({string}), plan {string} and member id {string}',
  (payerName: string, payerId: string, plan: string, memberId: string) => {
    coverageCard().contains('button', 'Add coverage').click();
    cy.get('mat-dialog-container').within(() => {
      cy.get('input[formcontrolname="payerName"]').type(payerName);
      cy.get('input[formcontrolname="payerId"]').type(payerId);
      cy.get('input[formcontrolname="planName"]').type(plan);
      cy.get('input[formcontrolname="memberId"]').type(memberId);
      cy.contains('button', 'Save').click();
    });
    cy.get('mat-dialog-container').should('not.exist');
  },
);

Then('the chart shows no active coverage', () => {
  coverageCard().should('contain.text', 'No active coverage on file.');
});

Then(
  'the chart shows active coverage from {string} on plan {string} with member id {string}',
  (payerName: string, plan: string, memberId: string) => {
    coverageCard().within(() => {
      cy.get('.app-coverage-card__payer').should('contain.text', payerName);
      cy.get('.app-coverage-card__plan').should('contain.text', plan);
      cy.get('.app-coverage-card__member').should('contain.text', memberId);
      cy.get('.cp-status-badge--success').should('contain.text', 'active');
    });
  },
);

Then('focus is back on the coverage edit button', () => {
  cy.focused().should('have.attr', 'aria-label', 'Edit coverage');
});

Then('the navigation has no {string} link', (name: string) => {
  cy.contains('nav a', name).should('not.exist');
});

Then('I am redirected away from the patient chart', () => {
  cy.location('pathname').should('not.contain', '/patients/');
});
