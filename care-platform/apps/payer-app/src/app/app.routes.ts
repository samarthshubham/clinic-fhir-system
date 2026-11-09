import { Route } from '@angular/router';
import { authGuard, guestGuard } from '@care-platform/auth';

const placeholderPage = () =>
  import('./features/placeholder/placeholder-page.component').then(
    (m) => m.PlaceholderPageComponent,
  );

export const appRoutes: Route[] = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./core/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', redirectTo: 'queue', pathMatch: 'full' },
      {
        path: 'queue',
        title: 'Queue · Praxis Health Plan',
        loadComponent: () =>
          import('./features/queue/queue.component').then(
            (m) => m.QueueComponent,
          ),
      },
      {
        path: 'members',
        title: 'Members · Praxis Health Plan',
        loadComponent: () =>
          import(
            './features/members/member-search/member-search.component'
          ).then((m) => m.MemberSearchComponent),
      },
      {
        path: 'members/:id',
        title: 'Member · Praxis Health Plan',
        loadComponent: () =>
          import(
            './features/members/member-detail/member-detail.component'
          ).then((m) => m.MemberDetailComponent),
      },
      {
        path: 'directory',
        title: 'Directory · Praxis Health Plan',
        loadComponent: placeholderPage,
        data: {
          heading: 'Directory',
          icon: 'local_hospital',
          description: 'Browse the plan’s provider directory.',
        },
      },
      {
        path: 'metrics',
        title: 'Metrics · Praxis Health Plan',
        loadComponent: placeholderPage,
        data: {
          heading: 'Metrics',
          icon: 'monitoring',
          description: 'Prior authorization volumes and turnaround times.',
        },
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
