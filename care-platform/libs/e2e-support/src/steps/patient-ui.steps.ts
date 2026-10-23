import { Given, Then, When } from '@badeball/cypress-cucumber-preprocessor';
import { PASSWORDS } from '../support/api';

const patientUrl = (): string => Cypress.env('patientUrl');

function inPatientApp<A extends Record<string, unknown>>(
  args: A,
  flow: (args: A) => void,
): void {
  if (Cypress.config('baseUrl') === patientUrl()) {
    flow(args);
  } else {
    cy.origin(patientUrl(), { args }, flow);
  }
}

// cy.origin callbacks are serialized and cannot capture outer variables, so each flow is self-contained.
Given(
  'the patient signs in to the patient app as {string}',
  (username: string) => {
    const args = { username, password: PASSWORDS[username] };
    inPatientApp(args, ({ username: user, password }) => {
      cy.visit('/login');
      cy.get('input[formcontrolname="username"]').clear().type(user);
      cy.get('input[formcontrolname="password"]')
        .clear()
        .type(password, { log: false });
      cy.contains('button', 'Sign in').click();
      cy.location('pathname').should('eq', '/home');
    });
  },
);

When(
  'the patient records blood pressure {int} over {int} in the patient app',
  (systolic: number, diastolic: number) => {
    inPatientApp({ systolic, diastolic }, (reading) => {
      cy.visit('/vitals/record');
      cy.get('#systolic').clear().type(String(reading.systolic));
      cy.get('#diastolic').clear().type(String(reading.diastolic));
      cy.contains('button', 'Continue').click();
      cy.contains('button', 'Continue').click();
      cy.contains('button', 'Continue').click();
      cy.contains('button', 'Save my readings').click();
      cy.contains('Saved. Great job!').should('be.visible');
    });
  },
);

Then(
  'the patient sees the blood pressure {string} on the vitals page',
  (reading: string) => {
    inPatientApp({ reading }, (expected) => {
      cy.visit('/vitals');
      cy.contains('.pa-chart-latest .v', expected.reading, {
        timeout: 15_000,
      }).should('be.visible');
    });
  },
);

Then('the patient app greets {string}', (firstName: string) => {
  inPatientApp({ firstName }, (expected) => {
    cy.contains('.pa-page-title', expected.firstName, {
      timeout: 15_000,
    }).should('be.visible');
  });
});

When(
  'the patient enters blood pressure {int} over {int} without saving',
  (systolic: number, diastolic: number) => {
    inPatientApp({ systolic, diastolic }, (reading) => {
      cy.visit('/vitals/record');
      cy.get('#systolic').clear().type(String(reading.systolic));
      cy.get('#diastolic').clear().type(String(reading.diastolic));
    });
  },
);

Then('the review step shows blood pressure {string}', (reading: string) => {
  inPatientApp({ reading }, (expected) => {
    cy.contains('button', 'Continue').click();
    cy.contains('button', 'Continue').click();
    cy.contains('button', 'Continue').click();
    cy.contains('Does this look right?').should('be.visible');
    cy.contains('.pa-confirm-row', 'Blood pressure').should(
      'contain.text',
      expected.reading,
    );
  });
});
