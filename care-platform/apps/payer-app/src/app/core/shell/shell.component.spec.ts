import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService, AuthUser } from '@care-platform/auth';
import { ThemeService } from '@core/theme.service';
import { QueueComponent } from '@features/queue/queue.component';
import { ShellComponent } from './shell.component';

describe('ShellComponent', () => {
  const reviewer: AuthUser = {
    username: 'reviewer',
    name: 'Dana Whitfield',
    title: 'Utilization management nurse',
    practitionerId: null,
    patientId: null,
    roles: [],
  };
  let logout: ReturnType<typeof vi.fn>;
  let toggle: ReturnType<typeof vi.fn>;
  let isDark: ReturnType<typeof signal<boolean>>;

  async function render(user: AuthUser = reviewer) {
    logout = vi.fn();
    toggle = vi.fn();
    isDark = signal(false);
    const current = signal<AuthUser | null>(user);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: '',
            component: ShellComponent,
            children: [{ path: 'queue', component: QueueComponent }],
          },
        ]),
        {
          provide: AuthService,
          useValue: {
            user: current,
            isAuthenticated: computed(() => current() !== null),
            logout,
          },
        },
        { provide: ThemeService, useValue: { isDark, toggle } },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/queue');
    await harness.fixture.whenStable();
    return { harness, el: harness.routeNativeElement as HTMLElement };
  }

  it('should list Queue, Members, Directory and Metrics in the sidenav', async () => {
    const { el } = await render();
    const links = Array.from(
      el.querySelectorAll<HTMLAnchorElement>('mat-sidenav nav a'),
    );

    expect(
      links.map((a) =>
        a.querySelector('[matListItemTitle]')?.textContent?.trim(),
      ),
    ).toEqual(['Queue', 'Members', 'Directory', 'Metrics']);
    expect(links.map((a) => a.getAttribute('href'))).toEqual([
      '/queue',
      '/members',
      '/directory',
      '/metrics',
    ]);
  });

  it('should mark the current page in the sidenav', async () => {
    const { el } = await render();
    const current = el.querySelector('a[aria-current="page"]');

    expect(current?.getAttribute('href')).toBe('/queue');
    expect(current?.classList).toContain('mdc-list-item--activated');
  });

  it('should render the routed page in the main region', async () => {
    const { el } = await render();

    expect(el.querySelector('main#main-content cp-queue')).not.toBeNull();
  });

  it('should show who is signed in', async () => {
    const { el } = await render();

    expect(el.querySelector('.app-shell__user')?.textContent).toContain(
      'Dana Whitfield',
    );
    expect(el.querySelector('.app-shell__user')?.textContent).toContain(
      'Utilization management nurse',
    );
  });

  it('should leave out the title when the account has none', async () => {
    const { el } = await render({ ...reviewer, title: null });

    expect(el.querySelector('.app-shell__user-title')).toBeNull();
  });

  it('should sign out', async () => {
    const { el } = await render();
    const signOut = Array.from(el.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Sign out',
    );

    signOut?.click();

    expect(logout).toHaveBeenCalled();
  });

  it('should toggle the theme', async () => {
    const { harness, el } = await render();
    const button = el.querySelector<HTMLButtonElement>(
      'button[aria-label="Switch to dark theme"]',
    );

    button?.click();
    expect(toggle).toHaveBeenCalled();

    isDark.set(true);
    await harness.fixture.whenStable();
    expect(
      el.querySelector('button[aria-label="Switch to light theme"]'),
    ).not.toBeNull();
  });
});
