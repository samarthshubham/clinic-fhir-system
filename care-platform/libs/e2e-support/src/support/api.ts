import { ctx, resolvePlaceholders } from './context';

export const PASSWORDS: Record<string, string> = {
  admin: 'Admin#2026',
  clinician: 'Clinician#2026',
  frontdesk: 'FrontDesk#2026',
  tony: 'Tony#2026',
  priya: 'Priya#2026',
  'e2e-patient': 'E2ePatient#2026',
  'e2e-patient-2': 'E2ePatient#2026',
};

const TOKEN_TTL_MS = 10 * 60_000;
const tokenCache = new Map<string, { token: string; at: number }>();

export const apiUrl = (): string =>
  Cypress.env('apiUrl') ?? 'http://localhost:9090';

export function tokenFor(username: string): Cypress.Chainable<string> {
  const cached = tokenCache.get(username);
  if (cached && Date.now() - cached.at < TOKEN_TTL_MS)
    return cy.wrap(cached.token, { log: false });
  const password = PASSWORDS[username];
  if (!password) throw new Error(`No known password for "${username}"`);
  return cy
    .request('POST', `${apiUrl()}/api/auth/login`, { username, password })
    .then((response) => {
      expect(response.status, `login as ${username}`).to.eq(200);
      const token = response.body.accessToken as string;
      tokenCache.set(username, { token, at: Date.now() });
      return token;
    });
}

export function apiRequest(
  method: string,
  path: string,
  body?: unknown,
  token: string | undefined = ctx.token,
): Cypress.Chainable<Cypress.Response<any>> {
  return cy.request({
    method,
    url: `${apiUrl()}${resolvePlaceholders(path)}`,
    body:
      typeof body === 'string' ? JSON.parse(resolvePlaceholders(body)) : body,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
}

export function asStaff<T>(
  username: string,
  method: string,
  path: string,
  body?: unknown,
): Cypress.Chainable<Cypress.Response<T>> {
  return tokenFor(username).then((token) =>
    apiRequest(method, path, body, token),
  );
}

export function createTestPatient(alias: string): Cypress.Chainable<string> {
  const lastName = `E2E-${alias}-${Date.now().toString(36)}`;
  return asStaff<{ id: string }>('clinician', 'POST', '/api/patients', {
    firstName: 'Test',
    lastName,
    dateOfBirth: '1980-05-15',
    gender: 'female',
  }).then((response) => {
    expect(response.status, 'create test patient').to.eq(201);
    const id = response.body.id;
    ctx.names[alias] = lastName;
    return cy.task('trackTestData', { patientIds: [id] }).then(() => id);
  });
}
