import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  provideRouter,
  Route,
  Router,
  withComponentInputBinding,
} from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import {
  AuthService,
  authGuard,
  guestGuard,
  provideAuth,
} from '@care-platform/auth';
import { appRoutes } from './app.routes';
import { ShellComponent } from '@core/shell/shell.component';
import { LoginComponent } from '@features/login/login.component';
import { QueueComponent } from '@features/queue/queue.component';
import { PlaceholderPageComponent } from '@features/placeholder/placeholder-page.component';
import { MemberSearchComponent } from '@features/members/member-search/member-search.component';
import { MemberDetailComponent } from '@features/members/member-detail/member-detail.component';

describe('appRoutes', () => {
  function find(routes: Route[] | undefined, path: string): Route {
    const found = routes?.find((r) => r.path === path);
    if (!found) throw new Error(`missing route ${path}`);
    return found;
  }

  const shell = find(appRoutes, '');
  const page = (path: string) => find(shell.children, path);

  it('should lazy load the login page for signed-out users only', async () => {
    const login = find(appRoutes, 'login');

    expect(login.canActivate).toEqual([guestGuard]);
    expect(await login.loadComponent?.()).toBe(LoginComponent);
  });

  it('should require sign-in for the shell and everything inside it', async () => {
    expect(shell.canActivate).toEqual([authGuard]);
    expect(await shell.loadComponent?.()).toBe(ShellComponent);
  });

  it('should open the queue by default', () => {
    expect(page('')).toMatchObject({ redirectTo: 'queue', pathMatch: 'full' });
  });

  it('should lazy load the queue', async () => {
    expect(await page('queue').loadComponent?.()).toBe(QueueComponent);
  });

  it('should lazy load member search and member detail', async () => {
    expect(await page('members').loadComponent?.()).toBe(MemberSearchComponent);
    expect(await page('members/:id').loadComponent?.()).toBe(
      MemberDetailComponent,
    );
  });

  it.each(['directory', 'metrics'])(
    'should show a placeholder for %s until its story lands',
    async (path) => {
      expect(await page(path).loadComponent?.()).toBe(PlaceholderPageComponent);
      expect(page(path).data).toMatchObject({
        heading: expect.any(String),
        icon: expect.any(String),
        description: expect.any(String),
      });
    },
  );

  it('should send unknown paths home', () => {
    expect(find(appRoutes, '**').redirectTo).toBe('');
  });

  describe('navigation', () => {
    async function navigate(url: string, signedIn: boolean) {
      const user = signal(
        signedIn
          ? { username: 'reviewer', name: 'Dana Whitfield', title: null }
          : null,
      );
      TestBed.configureTestingModule({
        providers: [
          provideRouter(appRoutes, withComponentInputBinding()),
          provideAuth({
            loginPath: '/login',
            homePath: '/queue',
            refreshTokenKey: 'test.refreshToken',
          }),
          {
            provide: AuthService,
            useValue: {
              user,
              isAuthenticated: computed(() => user() !== null),
              logout: vi.fn(),
            },
          },
        ],
      });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(url);
      return { harness, router: TestBed.inject(Router) };
    }

    it('should send signed-out users to the login page and back afterwards', async () => {
      const { router } = await navigate('/members', false);

      expect(router.url).toBe('/login?returnUrl=%2Fmembers');
    });

    it('should land signed-in users on the queue', async () => {
      const { harness, router } = await navigate('/', true);

      expect(router.url).toBe('/queue');
      expect(harness.routeNativeElement?.textContent).toContain(
        'No prior authorization requests to review',
      );
    });

    it('should keep signed-in users away from the login page', async () => {
      const { router } = await navigate('/login', true);

      expect(router.url).toBe('/queue');
    });

    it('should bind the placeholder text from the route', async () => {
      const { harness } = await navigate('/directory', true);

      expect(harness.routeNativeElement?.textContent).toContain(
        'Browse the plan’s provider directory.',
      );
    });
  });
});
