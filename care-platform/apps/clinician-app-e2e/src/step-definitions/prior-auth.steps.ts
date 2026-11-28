import { Given, Then, When } from '@badeball/cypress-cucumber-preprocessor';

interface QueueItem {
  claimId: string;
  authorizationReference: string;
}

const PAYER_API = 'http://localhost:9095/api';
const orderRow = (display: string) =>
  cy.contains('li.order-row', display, { timeout: 15_000 });

Given('the payer can validate a PAS request for a cardiac stress test', () => {
  cy.task('ensurePasTerminology', null, { timeout: 780_000 });
});

When(
  'I submit prior authorization for {string} in place of service {string}',
  (display: string, place: string) => {
    cy.window().then((window) =>
      cy.spy(window.console, 'error').as('priorAuthConsoleErrors'),
    );
    cy.intercept('POST', '/api/prior-auths').as('submitPriorAuth');
    orderRow(display).contains('button', 'Submit prior authorization').click();
    const dialog = () => cy.get('cp-prior-auth-dialog');
    dialog().find('input[formcontrolname="placeOfService"]').type(place);
    dialog().contains('button', 'Submit to payer').click();
    cy.wait<{ orderId: string }>('@submitPriorAuth', {
      responseTimeout: 75_000,
    }).then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      cy.wrap(request.body.orderId).as('priorAuthOrderId');
    });
  },
);

Then(
  'the order {string} shows prior authorization {string}',
  (display: string, status: string) => {
    orderRow(display)
      .contains('[role="status"]', `Prior authorization: ${status}`, {
        timeout: 75_000,
      })
      .should('be.visible');
  },
);

When("the payer approves the test patient's cardiac stress test", () => {
  cy.get<string>('@priorAuthOrderId').then((orderId) => {
    cy.request<{ entry: { resource: { id: string } }[] }>(
      `http://localhost:8081/fhir/Claim?identifier=${encodeURIComponent('http://praxis.clinic/fhir/identifier/claim|PX-PA-' + orderId)}`,
    ).then(({ body }) => {
      expect(body.entry).to.have.length(1);
      const claimId = body.entry[0].resource.id;
      cy.request<{ accessToken: string }>('POST', `${PAYER_API}/auth/login`, {
        username: 'reviewer',
        password: 'Reviewer#2026',
      }).then(({ body: { accessToken } }) => {
        cy.request<{ items: QueueItem[] }>({
          url: `${PAYER_API}/prior-auths?status=pended`,
          headers: { Authorization: `Bearer ${accessToken}` },
        }).then(({ body }) => {
          const row = body.items.find((item) => item.claimId === claimId);
          expect(row, 'submitted case in reviewer queue').not.to.be.undefined;
          cy.wrap(row!.authorizationReference).as('priorAuthNumber');
        });
        cy.request({
          method: 'POST',
          url: `${PAYER_API}/prior-auths/${claimId}/decision`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: { decision: 'APPROVE' },
        })
          .its('status')
          .should('eq', 200);
      });
    });
  });
});

Then(
  'the order {string} shows prior authorization {string} and its authorization number',
  (display: string, status: string) => {
    cy.get<string>('@priorAuthNumber').then((authNumber) => {
      orderRow(display)
        .contains('[role="status"]', `Prior authorization: ${status}`, {
          timeout: 90_000,
        })
        .should('contain.text', authNumber);
    });
  },
);

When('I open the clinician Inbox for the prior authorization decision', () => {
  cy.contains('a', 'Inbox').click();
});

Then("the test patient's Inbox entry says {string}", (message: string) => {
  cy.contains('li.inbox__item', message, { timeout: 20_000 }).should(
    'contain.text',
    'E2E-pas-',
  );
});

Then('the clinician app logged no errors during prior authorization', () => {
  cy.get('@priorAuthConsoleErrors').should('not.have.been.called');
});
