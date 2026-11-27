import { Given, Then } from '@badeball/cypress-cucumber-preprocessor';

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
