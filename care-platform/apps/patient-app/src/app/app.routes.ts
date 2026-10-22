import { Route } from '@angular/router';
import { authGuard, guestGuard } from '@care-platform/auth';

export const appRoutes: Route[] = [
  {
    path: '',
    redirectTo: 'home',
    pathMatch: 'full',
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'home',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'vitals',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/vitals/vitals.component').then(
        (m) => m.VitalsComponent,
      ),
  },
  {
    path: 'vitals/record',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/vitals/record-vitals/record-vitals.component').then(
        (m) => m.RecordVitalsComponent,
      ),
  },
  {
    path: 'messages',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/messages/messages.component').then(
        (m) => m.MessagesComponent,
      ),
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/profile/profile.component').then(
        (m) => m.ProfileComponent,
      ),
  },
];
