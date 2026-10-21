import { appRoutes } from './app.routes';
import { PatientRosterComponent } from './features/patients/patient-roster/patient-roster.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';

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
});
