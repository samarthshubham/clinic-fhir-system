import { Given, Then, When } from '@badeball/cypress-cucumber-preprocessor';

Given(
  'a member with an overdue pended cardiac stress test authorization',
  () => {
    const memberId = `PHP-9${String(Math.floor(Math.random() * 10_000)).padStart(4, '0')}`;
    cy.wrap(memberId).as('queueMemberId');
    cy.task('createPendedRequest', memberId, { timeout: 90_000 });
    cy.on('window:before:load', (window) => {
      cy.spy(window.console, 'error').as('queueConsoleErrors');
    });
  },
);

Then('the queue shows the pended request with its deadline and breach', () => {
  cy.get<string>('@queueMemberId').then((memberId) => {
    cy.contains('cp-queue tbody tr', `E2E-Queue-${memberId}`)
      .should('be.visible')
      .and('contain.text', 'Cardiac stress test')
      .and('contain.text', 'Standard')
      .and('contain.text', 'Breached')
      .and('contain.text', 'overdue')
      .find('time')
      .should('have.attr', 'datetime');
  });
  cy.get('@queueConsoleErrors').should('not.have.been.called');
});

When('the reviewer opens the pended authorization', () => {
  cy.get<string>('@queueMemberId').then((memberId) => {
    cy.contains('cp-queue tbody tr', `E2E-Queue-${memberId}`)
      .contains('a', 'Cardiac stress test')
      .click();
  });
  cy.contains('h1', 'Prior authorization review').should('be.visible');
  cy.get('cp-prior-auth-review')
    .should('contain.text', 'Cardiac stress test')
    .and('contain.text', 'Prior authorization required')
    .and('contain.text', 'Exertional chest discomfort')
    .and('contain.text', 'Cardiology note');
});

When('the reviewer approves the authorization', () => {
  cy.intercept('POST', '/api/prior-auths/*/decision').as('saveDecision');
  cy.contains('button', 'Approve').click();
  cy.wait('@saveDecision').its('response.statusCode').should('eq', 200);
});

Then('the approval is saved and removed from the queue', () => {
  cy.contains('[role=status]', 'Decision saved.').should('be.visible');
  cy.contains('a', 'Back to review queue').click();
  cy.contains('h1', 'Review queue').should('be.visible');
  cy.get('mat-progress-bar').should('not.exist');
  cy.get<string>('@queueMemberId').then((memberId) => {
    cy.contains('cp-queue tbody tr', `E2E-Queue-${memberId}`).should(
      'not.exist',
    );
  });
  cy.get('@queueConsoleErrors').should('not.have.been.called');
});

Then('denial requires both a reason code and reason text', () => {
  cy.contains('button', 'Deny').click();
  cy.contains('button', 'Confirm denial').should('be.disabled');
  cy.contains('mat-form-field', 'Reason code').find('input').type('criteria');
  cy.contains('button', 'Confirm denial').should('be.disabled');
  cy.contains('mat-form-field', 'Reason text')
    .find('textarea')
    .type('Criteria not met');
  cy.contains('button', 'Confirm denial').should('be.enabled');
  cy.get('@queueConsoleErrors').should('not.have.been.called');
});
