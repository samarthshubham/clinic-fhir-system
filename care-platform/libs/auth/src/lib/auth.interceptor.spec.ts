import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: {
    accessToken: ReturnType<typeof vi.fn>;
    refresh: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    auth = {
      accessToken: vi.fn(() => 'access-1'),
      refresh: vi.fn(() => of('access-2')),
      logout: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('should attach the access token to API calls', () => {
    http.get('/api/patients').subscribe();

    const req = backend.expectOne('/api/patients');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
    req.flush([]);
  });

  it('should not attach a token to auth endpoints or other hosts', () => {
    http.post('/api/auth/login', {}).subscribe();
    http.get('/assets/logo.svg').subscribe();

    expect(
      backend.expectOne('/api/auth/login').request.headers.has('Authorization'),
    ).toBe(false);
    expect(
      backend
        .expectOne('/assets/logo.svg')
        .request.headers.has('Authorization'),
    ).toBe(false);
  });

  it('should send no header while signed out', () => {
    auth.accessToken.mockReturnValue(null);

    http.get('/api/patients').subscribe();

    expect(
      backend.expectOne('/api/patients').request.headers.has('Authorization'),
    ).toBe(false);
  });

  it('should refresh once on 401 and retry with the new token', () => {
    let body: unknown;
    http.get('/api/patients').subscribe((b) => (body = b));

    backend
      .expectOne('/api/patients')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    const retry = backend.expectOne('/api/patients');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer access-2');
    retry.flush(['ok']);

    expect(auth.refresh).toHaveBeenCalledTimes(1);
    expect(body).toEqual(['ok']);
  });

  it('should sign out and surface the 401 when refresh fails', () => {
    auth.refresh.mockReturnValue(throwError(() => new Error('expired')));
    let status: number | undefined;
    http.get('/api/patients').subscribe({ error: (e) => (status = e.status) });

    backend
      .expectOne('/api/patients')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.logout).toHaveBeenCalled();
    expect(status).toBe(401);
  });

  it('should pass other errors straight through', () => {
    let status: number | undefined;
    http.get('/api/patients').subscribe({ error: (e) => (status = e.status) });

    backend
      .expectOne('/api/patients')
      .flush(null, { status: 403, statusText: 'Forbidden' });

    expect(status).toBe(403);
    expect(auth.refresh).not.toHaveBeenCalled();
  });
});
