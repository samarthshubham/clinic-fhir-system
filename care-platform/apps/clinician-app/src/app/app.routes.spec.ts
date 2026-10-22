import { appRoutes } from './app.routes';
import { PatientRosterComponent } from './features/patients/patient-roster/patient-roster.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { LoginComponent } from './features/login/login.component';
import { InboxComponent } from './features/inbox/inbox.component';
import { authGuard, guestGuard } from '@care-platform/auth';

describe('appRoutes', () => {
  function route(path: string) {
    const found = appRoutes.find((r) => r.path === path);
    if (!found) throw new Error(`missing route ${path}`);
    return found;
  }

  it('should redirect the root to the patient roster', () => {
    expect(route('')).toMatchObject({
      redirectTo: 'patients',
      pathMatch: 'full',
    });
  });

  it('should lazy load the patient roster', async () => {
    expect(await route('patients').loadComponent?.()).toBe(
      PatientRosterComponent,
    );
  });

  it('should lazy load the dashboard for a patient', async () => {
    expect(await route('patients/:id').loadComponent?.()).toBe(
      DashboardComponent,
    );
  });

  it('should lazy load the login page for signed-out users only', async () => {
    expect(route('login').canActivate).toEqual([guestGuard]);
    expect(await route('login').loadComponent?.()).toBe(LoginComponent);
  });

  it('should require sign-in for the roster and a clinical role for charts', () => {
    expect(route('patients').canActivate?.[0]).toBe(authGuard);
    expect(route('patients').canActivate).toHaveLength(2);
    expect(route('patients/:id').canActivate?.[0]).toBe(authGuard);
    expect(route('patients/:id').canActivate).toHaveLength(2);
  });

  it('should lazy load the inbox for clinical roles', async () => {
    expect(route('inbox').canActivate?.[0]).toBe(authGuard);
    expect(route('inbox').canActivate).toHaveLength(2);
    expect(await route('inbox').loadComponent?.()).toBe(InboxComponent);
  });
});
