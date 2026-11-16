import { Then, When } from '@badeball/cypress-cucumber-preprocessor';

const orderDialog = () => cy.get('mat-dialog-container');
const pendingOrder = (display: string) =>
  cy.contains('li.order-row', display, { timeout: 15_000 });

function choose(control: string, option: string): void {
  orderDialog().find(`mat-select[formcontrolname="${control}"]`).click();
  cy.contains('mat-option', option).click();
}

When(
  'I place the order {string} with {word} code {string} in category {string}',
  (display: string, system: string, code: string, category: string) => {
    cy.contains('button', 'New order', { timeout: 15_000 }).click();
    orderDialog().find('input[formcontrolname="display"]').type(display).blur();
    choose('category', category);
    choose('codeSystem', system);
    orderDialog().find('input[formcontrolname="code"]').type(code).blur();
    orderDialog().contains('button', 'Place order').click();
    orderDialog()
      .find('h2', { timeout: 15_000 })
      .should('contain.text', 'Order placed');
  },
);

Then('the order dialog shows the coverage badge {string}', (label: string) => {
  orderDialog().find('.cp-status-badge').should('have.text', label);
});

Then('the order dialog shows the card {string}', (summary: string) => {
  orderDialog().find('.app-crd-card__summary').should('contain.text', summary);
});

Then('the order dialog offers {string}', (action: string) => {
  orderDialog().contains('button', action).should('be.visible');
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
      cy.contains('button', 'Complete documentation').should('be.visible');
    });
  },
);
