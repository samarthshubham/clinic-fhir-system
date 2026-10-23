import { DataTable, Given, Then, When } from '@badeball/cypress-cucumber-preprocessor';
import { apiRequest, asStaff, createTestPatient, tokenFor } from '../support/api';
import { ctx, isoDate, resolveId, resolvePlaceholders } from '../support/context';

function valueAt(body: unknown, path: string): any {
  return path
    .split('.')
    .reduce<any>((current, key) => (current == null ? undefined : current[key]), body);
}

function trackCreatedStaffOrPatient(method: string, path: string, response: Cypress.Response<any>): void {
  if (method.toUpperCase() !== 'POST' || response.status !== 201) return;
  if (path === '/api/practitioners') {
    cy.task('trackTestData', { practitionerIds: [response.body.id] });
  } else if (path === '/api/patients') {
    cy.task('trackTestData', { patientIds: [response.body.id] });
  }
}

function listAt(path: string): any[] {
  const value = path === '' ? ctx.response?.body : valueAt(ctx.response?.body, path);
  expect(value, `list at "${path}"`).to.be.an('array');
  return value;
}

Given('I am signed in to the API as {string}', (username: string) => {
  tokenFor(username).then((token) => {
    ctx.token = token;
  });
});

Given('I am not signed in to the API', () => {
  ctx.token = undefined;
});

Given('I am signed in to the API with an invalid token', () => {
  ctx.token = 'not.a.valid.token';
});

Given('a test patient {string}', (alias: string) => {
  createTestPatient(alias).then((id) => {
    ctx.ids[alias] = id;
    ctx.ids['patient'] ??= id;
  });
});

Given('the e2e login patients exist', () => {
  const patients = [
    { alias: 'patient', id: 'e2e-patient-1', firstName: 'Eve' },
    { alias: 'other', id: 'e2e-patient-2', firstName: 'Evan' },
  ];
  for (const { alias, id, firstName } of patients) {
    cy.task('upsertTestPatient', {
      id,
      firstName,
      lastName: 'E2E-login',
      dateOfBirth: '1985-03-14',
      gender: 'female',
    });
    ctx.ids[alias] = id;
  }
});

Given('{string} creates {string} with POST {string}:', (username: string, alias: string, path: string, body: string) => {
  asStaff<{ id: string }>(username, 'POST', path, resolvePlaceholders(body)).then((response) => {
    expect(response.status, `POST ${path}`).to.be.oneOf([200, 201]);
    ctx.ids[alias] = (Array.isArray(response.body) ? response.body[0] : response.body).id;
    if (path.startsWith('/api/practitioners')) {
      cy.task('trackTestData', { practitionerIds: [response.body.id] });
    }
  });
});

Given('the test patient has these vitals:', (table: DataTable) => {
  const rows = table
    .hashes()
    .map((row) => ({ ...row, daysAgo: Number(row['daysAgo']) }))
    .sort((a, b) => b.daysAgo - a.daysAgo);
  const patientId = resolveId('patient');
  const fields: Record<string, string> = {
    systolic: 'systolicBp',
    diastolic: 'diastolicBp',
    spo2: 'spo2Percent',
    heartRate: 'heartRateBpm',
    temperature: 'temperatureCelsius',
    weight: 'weightKg',
  };
  for (const row of rows) {
    const body: Record<string, unknown> = {
      patientId,
      effectiveDate: isoDate(-row.daysAgo),
    };
    for (const [column, field] of Object.entries(fields)) {
      const cell = (row as Record<string, unknown>)[column];
      if (cell !== undefined && cell !== '') body[field] = Number(cell);
    }
    asStaff('clinician', 'POST', '/api/vitals', body).then((response) => {
      expect(response.status, 'record vitals').to.eq(201);
    });
  }
});

When('I send {word} {string}', (method: string, path: string) => {
  apiRequest(method, path).then((response) => {
    ctx.response = response;
    trackCreatedStaffOrPatient(method, path, response);
  });
});

When('I send {word} {string} with body:', (method: string, path: string, body: string) => {
  apiRequest(method, path, body).then((response) => {
    ctx.response = response;
    trackCreatedStaffOrPatient(method, path, response);
  });
});

When('I track the response id as the test patient', () => {
  const id = String(valueAt(ctx.response?.body, 'id'));
  ctx.ids['patient'] = id;
  cy.task('trackTestData', { patientIds: [id] });
});

Given('the test patient has {int} encounters', (count: number) => {
  const patientId = resolveId('patient');
  for (let i = 1; i <= count; i++) {
    asStaff('clinician', 'POST', '/api/encounters', {
      patientId,
      visitDate: isoDate(-i),
      reason: `Visit ${i}`,
      status: 'finished',
    }).then((response) => {
      expect(response.status, `encounter ${i}`).to.eq(201);
    });
  }
});

Given('the FHIR server is stopped', () => {
  cy.task('stopFhir', null, { timeout: 60_000 });
});

When('I use the remembered {string} as my bearer token', (alias: string) => {
  ctx.token = resolveId(alias);
});

When('I remember the {string} of the last item as {string}', (key: string, alias: string) => {
  const items = listAt('');
  ctx.ids[alias] = String(valueAt(items[items.length - 1], key));
});

When('I remember the response field {string} as {string}', (path: string, alias: string) => {
  ctx.ids[alias] = String(valueAt(ctx.response?.body, path));
});

Then('the response status is {int}', (status: number) => {
  expect(ctx.response?.status, JSON.stringify(ctx.response?.body)).to.eq(status);
});

Then('the response field {string} is {string}', (path: string, expected: string) => {
  expect(String(valueAt(ctx.response?.body, path))).to.eq(resolvePlaceholders(expected));
});

Then('the response field {string} is not empty', (path: string) => {
  expect(valueAt(ctx.response?.body, path)).to.not.be.oneOf([undefined, null, '']);
});

Then('the response field {string} is null', (path: string) => {
  expect(valueAt(ctx.response?.body, path)).to.be.oneOf([undefined, null]);
});

Then('the response field {string} contains {string}', (path: string, text: string) => {
  expect(String(valueAt(ctx.response?.body, path))).to.contain(text);
});

Then('the response has {int} items', (count: number) => {
  const body = ctx.response?.body;
  const items = Array.isArray(body) ? body : body?.items;
  expect(items, 'items').to.have.length(count);
});

Then('the response field {string} has {int} items', (path: string, count: number) => {
  expect(listAt(path)).to.have.length(count);
});

Then('the response field {string} has at most {int} items', (path: string, max: number) => {
  expect(listAt(path).length).to.be.at.most(max);
});

Then('the response field {string} has at least {int} items', (path: string, min: number) => {
  expect(listAt(path).length).to.be.at.least(min);
});

Then('some item in {string} has {string} equal to {string}', (path: string, key: string, expected: string) => {
  const matches = listAt(path).filter((item) => String(valueAt(item, key)) === resolvePlaceholders(expected));
  expect(matches.length, `items in "${path}" with ${key}=${expected}`).to.be.greaterThan(0);
});

Then('no item in {string} has {string} equal to {string}', (path: string, key: string, expected: string) => {
  const matches = listAt(path).filter((item) => String(valueAt(item, key)) === resolvePlaceholders(expected));
  expect(matches, `items in "${path}" with ${key}=${expected}`).to.have.length(0);
});

Then('every item in {string} has {string} equal to {string}', (path: string, key: string, expected: string) => {
  for (const item of listAt(path)) {
    expect(String(valueAt(item, key))).to.eq(resolvePlaceholders(expected));
  }
});

Then('the {string} dates of {string} are newest first', (key: string, path: string) => {
  const dates = listAt(path).map((item) => String(valueAt(item, key)));
  expect(dates).to.deep.eq([...dates].sort().reverse());
});

Then('the error message contains {string}', (text: string) => {
  expect(String(ctx.response?.body?.message)).to.contain(text);
});
