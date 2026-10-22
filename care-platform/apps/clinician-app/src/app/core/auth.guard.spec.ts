import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { Role } from './auth.models';

describe('auth guards', () => {
  let signedIn: boolean;
  let roles: Role[];

  beforeEach(() => {
    signedIn = false;
    roles = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: () => signedIn,
            hasRole: (...wanted: Role[]) =>
              wanted.some((role) => roles.includes(role)),
          },
        },
      ],
    });
  });

  function run(guard: typeof authGuard, url = '/patients/p1') {
    return TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  }

  function serialize(result: unknown) {
    return TestBed.inject(Router).serializeUrl(result as UrlTree);
  }

  it('authGuard should send signed-out users to login with a return url', () => {
    expect(serialize(run(authGuard))).toBe('/login?returnUrl=%2Fpatients%2Fp1');
  });

  it('authGuard should let signed-in users through', () => {
    signedIn = true;

    expect(run(authGuard)).toBe(true);
  });

  it('guestGuard should keep signed-in users away from login', () => {
    expect(run(guestGuard)).toBe(true);

    signedIn = true;

    expect(serialize(run(guestGuard))).toBe('/patients');
  });

  it('roleGuard should allow listed roles and redirect others', () => {
    const clinicalOnly = roleGuard('ADMIN', 'CLINICIAN');
    roles = ['CLINICIAN'];

    expect(run(clinicalOnly)).toBe(true);

    roles = ['FRONT_DESK'];

    expect(serialize(run(clinicalOnly))).toBe('/patients');
  });
});
