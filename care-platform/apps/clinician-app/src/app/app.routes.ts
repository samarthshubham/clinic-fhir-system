import { Route } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from '@core/auth.guard';

export const appRoutes: Route[] = [
  {
    path: '',
    redirectTo: 'patients',
    pathMatch: 'full',
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'patients',
    canActivate: [authGuard],
    loadComponent: () =>
      import(
        './features/patients/patient-roster/patient-roster.component'
      ).then((m) => m.PatientRosterComponent),
  },
  {
    path: 'patients/:id',
    canActivate: [authGuard, roleGuard('ADMIN', 'CLINICIAN')],
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then(
        (m) => m.DashboardComponent,
      ),
  },
  {
    path: 'inbox',
    canActivate: [authGuard, roleGuard('ADMIN', 'CLINICIAN')],
    loadComponent: () =>
      import('./features/inbox/inbox.component').then((m) => m.InboxComponent),
  },
];
