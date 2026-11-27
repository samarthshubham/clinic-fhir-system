import { Given, Then, When } from '@badeball/cypress-cucumber-preprocessor';

const directory = () => cy.get('cp-referral-directory');

Given('the payer publishes the Plan-Net directory', () => {
  // A cold terminology import and native lookup can take up to 13 minutes.
  cy.task('ensurePlanNetFixtures', null, { timeout: 1_080_000 });
});

When('I open the provider directory', () => {
  cy.window().then((window) => {
    cy.spy(window.console, 'error').as('directoryConsoleErrors');
  });
  cy.contains('button', 'Find in-network provider').click();
});

When(
  'I search the directory for {string} in {string}',
  (specialty: string, city: string) => {
    directory().within(() => {
      cy.get('input[formcontrolname="specialty"]').clear();
      cy.get('input[formcontrolname="specialty"]').type(specialty);
      cy.get('input[formcontrolname="city"]').clear();
      cy.get('input[formcontrolname="city"]').type(city);
      cy.contains('button', 'Search').click();
    });
  },
);

Then(
  'the directory lists {string} at {string}',
  (provider: string, address: string) => {
    directory()
      .contains('li', provider, { timeout: 20_000 })
      .should('be.visible')
      .and('contain.text', address);
  },
);

Then('the directory has no matching providers', () => {
  directory().contains('No providers match').should('be.visible');
  cy.get('@directoryConsoleErrors').should('not.have.been.called');
});
