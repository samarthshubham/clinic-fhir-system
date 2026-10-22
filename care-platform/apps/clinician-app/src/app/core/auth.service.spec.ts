import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService, REFRESH_TOKEN_KEY } from './auth.service';
import { TokenResponse } from './auth.models';

function tokens(overrides: Partial<TokenResponse> = {}): TokenResponse {
  return {
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: {
      username: 'clinician',
      name: 'Anika Patel',
      title: 'Internal Medicine',
      practitionerId: 'user-clinician',
      roles: ['CLINICIAN'],
    },
    ...overrides,
  };
}

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should start signed out', () => {
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.user()).toBeNull();
    expect(auth.accessToken()).toBeNull();
    expect(auth.hasRole('CLINICIAN')).toBe(false);
  });

  it('should keep the access token in memory and the refresh token in storage', () => {
    let user: unknown;
    auth.login('clinician', 'pw').subscribe((u) => (user = u));

    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'clinician', password: 'pw' });
    req.flush(tokens());

    expect(user).toEqual(tokens().user);
    expect(auth.accessToken()).toBe('access-1');
    expect(auth.hasRole('ADMIN', 'CLINICIAN')).toBe(true);
    expect(auth.hasRole('FRONT_DESK')).toBe(false);
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('refresh-1');
  });

  it('should share one refresh call between concurrent callers', () => {
    localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-1');
    const received: string[] = [];

    auth.refresh().subscribe((t) => received.push(t));
    auth.refresh().subscribe((t) => received.push(t));

    const req = http.expectOne('/api/auth/refresh');
    expect(req.request.body).toEqual({ refreshToken: 'refresh-1' });
    req.flush(tokens({ accessToken: 'access-2', refreshToken: 'refresh-2' }));

    expect(received).toEqual(['access-2', 'access-2']);
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('refresh-2');

    auth.refresh().subscribe();
    http.expectOne('/api/auth/refresh').flush(tokens());
  });

  it('should clear the session when refresh is rejected', () => {
    localStorage.setItem(REFRESH_TOKEN_KEY, 'expired');
    let failed = false;

    auth.refresh().subscribe({ error: () => (failed = true) });
    http
      .expectOne('/api/auth/refresh')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull();
  });

  it('should fail refresh without a stored refresh token', () => {
    let failed = false;

    auth.refresh().subscribe({ error: () => (failed = true) });

    expect(failed).toBe(true);
  });

  it('should restore a session from the stored refresh token', () => {
    localStorage.setItem(REFRESH_TOKEN_KEY, 'refresh-1');

    auth.restoreSession().subscribe();
    http.expectOne('/api/auth/refresh').flush(tokens());

    expect(auth.user()?.name).toBe('Anika Patel');
  });

  it('should restore silently when the refresh token is rejected', () => {
    localStorage.setItem(REFRESH_TOKEN_KEY, 'expired');
    let completed = false;

    auth.restoreSession().subscribe({ complete: () => (completed = true) });
    http
      .expectOne('/api/auth/refresh')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(completed).toBe(true);
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('should skip restoring without a stored refresh token', () => {
    let completed = false;

    auth.restoreSession().subscribe({ complete: () => (completed = true) });

    expect(completed).toBe(true);
  });

  it('should forget everything and go to login on logout', () => {
    const navigate = vi
      .spyOn(TestBed.inject(Router), 'navigate')
      .mockResolvedValue(true);
    auth.login('clinician', 'pw').subscribe();
    http.expectOne('/api/auth/login').flush(tokens());

    auth.logout();

    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
