import { Given, Then, When } from '@badeball/cypress-cucumber-preprocessor';
import { asStaff } from '../support/api';
import { ctx, isoDate } from '../support/context';

const orderDialog = () => cy.get('mat-dialog-container');
const pendingOrder = (display: string) =>
  cy.contains('li.order-row', display, { timeout: 15_000 });

function choose(control: string, option: string): void {
  orderDialog().find(`mat-select[formcontrolname="${control}"]`).click();
  cy.contains('mat-option', option).click();
}

function placeOrder(
  entryPoint: string,
  display: string,
  system: string,
  code: string,
  category: string,
): void {
  cy.get(entryPoint)
    .contains('button', 'New order', { timeout: 15_000 })
    .click();
  orderDialog().find('input[formcontrolname="display"]').type(display).blur();
  choose('category', category);
  choose('codeSystem', system);
  orderDialog().find('input[formcontrolname="code"]').type(code).blur();
  orderDialog().contains('button', 'Place order').click();
  orderDialog()
    .find('h2', { timeout: 15_000 })
    .should('contain.text', 'Order placed');
}

When(
  'I place the order {string} with {word} code {string} in category {string}',
  (display: string, system: string, code: string, category: string) =>
    placeOrder('cp-care-panel', display, system, code, category),
);

When(
  'I place the order {string} with {word} code {string} in category {string} from the patient header',
  (display: string, system: string, code: string, category: string) =>
    placeOrder('cp-patient-header', display, system, code, category),
);

Then('the order dialog shows the coverage badge {string}', (label: string) => {
  orderDialog().find('.cp-status-badge').should('have.text', label);
});

Then('the order dialog shows the card {string}', (summary: string) => {
  orderDialog().find('.app-crd-card__summary').should('contain.text', summary);
});

Then('the order dialog offers {string}', (action: string) => {
  orderDialog()
    .contains('button', action)
    .should('be.visible')
    .and('be.enabled');
});

When('I close the order dialog', () => {
  orderDialog().contains('button', 'Done').click();
  orderDialog().should('not.exist');
});

Then(
  'the pending order {string} shows the coverage badge {string} and the card {string}',
  (display: string, label: string, summary: string) => {
    pendingOrder(display).within(() => {
      cy.get('.cp-status-badge').should('have.text', label);
      cy.get('.app-crd-card__summary').should('contain.text', summary);
      cy.contains('button', 'Complete documentation')
        .scrollIntoView()
        .should('be.visible');
    });
  },
);

const documentation = () => cy.get('cp-documentation-dialog');
const question = (linkId: string) =>
  documentation().find(`[data-link-id="${linkId}"]`);

Given('the payer offers the MRI documentation questionnaire', () => {
  cy.task('ensureMriDtrFixtures', null, { timeout: 780_000 });
});

Given('the test patient has a chart diagnosis of low back pain', () => {
  asStaff('clinician', 'POST', '/api/conditions', {
    patientId: ctx.ids['patient'],
    code: '279039007',
    display: 'Low back pain',
    clinicalStatus: 'active',
    onsetDate: isoDate(-60),
  })
    .its('status')
    .should('eq', 201);
});

When('I open documentation from the order dialog', () => {
  cy.window().then((window) => {
    cy.spy(window.console, 'error').as('documentationConsoleErrors');
  });
  orderDialog().contains('button', 'Complete documentation').click();
});

Then('the MRI form shows the diagnosis prefilled from the chart', () => {
  documentation()
    .contains('h2', 'MRI lumbar spine documentation', { timeout: 20_000 })
    .should('be.visible');
  question('primary-diagnosis')
    .find('input')
    .should('have.value', 'Low back pain');
  question('primary-diagnosis')
    .contains('Prefilled from the chart')
    .should('exist');
});

When(
  'I enter {int} weeks of conservative therapy and save the draft',
  (weeks: number) => {
    question('conservative-therapy-weeks')
      .find('input')
      .clear()
      .type(String(weeks));
    documentation().contains('button', 'Save draft').click();
    documentation()
      .contains('[role="status"]', 'Draft saved.')
      .should('be.visible');
  },
);

When('I close documentation', () => {
  documentation().contains('button', 'Close').click();
  documentation().should('not.exist');
});

When(
  'I reopen documentation from the pending order {string}',
  (display: string) => {
    pendingOrder(display).contains('button', 'Complete documentation').click();
  },
);

Then(
  'the MRI form shows {int} weeks of conservative therapy',
  (weeks: number) => {
    question('conservative-therapy-weeks')
      .find('input')
      .should('have.value', String(weeks));
  },
);

When('I record a neurological deficit and no prior imaging', () => {
  question('neurological-deficit').find('mat-select').click();
  cy.contains('mat-option', /^Yes$/).click();
  question('prior-imaging-done').find('mat-select').click();
  cy.contains('mat-option', /^No$/).click();
  question('prior-imaging-date').should('not.exist');
});

When('I try to complete the MRI documentation', () => {
  documentation()
    .find('button[type="submit"]')
    .should('contain.text', 'Complete')
    .click();
});

Then('the deficit description is required', () => {
  question('neurological-deficit-description')
    .contains('[role="alert"]', 'An answer is required.')
    .should('exist');
  question('neurological-deficit-description')
    .find('textarea')
    .should('be.focused');
});

When('I describe the deficit and complete the MRI documentation', () => {
  question('neurological-deficit-description')
    .find('textarea')
    .type('Progressive left ankle weakness');
  documentation()
    .find('button[type="submit"]')
    .should('contain.text', 'Complete')
    .click();
});

Then('the MRI documentation is complete', () => {
  documentation()
    .contains('[role="status"]', 'Documentation complete.', { timeout: 20_000 })
    .should('be.visible');
  question('neurological-deficit-description').should(
    'contain.text',
    'Progressive left ankle weakness',
  );
  documentation().contains('button', 'Save draft').should('not.exist');
});

Then('the clinician app logged no errors during documentation', () => {
  cy.get('@documentationConsoleErrors').should('not.have.been.called');
});
