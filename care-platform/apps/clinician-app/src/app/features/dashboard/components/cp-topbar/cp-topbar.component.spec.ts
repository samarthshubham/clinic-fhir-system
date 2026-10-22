import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CpTopbarComponent } from './cp-topbar.component';
import { ThemeService } from '@core/theme.service';
import { ToastService } from '@core/toast.service';
import { computed, signal } from '@angular/core';
import { AuthService } from '@core/auth.service';
import { AuthUser, Role } from '@core/auth.models';

function fakeAuth(...roles: Role[]) {
  const user = signal<AuthUser | null>(
    roles.length
      ? {
          username: 'clinician',
          name: 'Anika Patel',
          title: 'Internal Medicine',
          practitionerId: 'user-clinician',
          roles,
        }
      : null,
  );
  return {
    user,
    isAuthenticated: computed(() => user() !== null),
    hasRole: (...wanted: Role[]) =>
      wanted.some((role) => user()?.roles.includes(role) ?? false),
    logout: vi.fn(),
  };
}

describe('CpTopbarComponent', () => {
  let auth: ReturnType<typeof fakeAuth>;

  beforeEach(async () => {
    localStorage.clear();
    auth = fakeAuth('CLINICIAN');
    await TestBed.configureTestingModule({
      imports: [CpTopbarComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
  });

  it('should link to the patient roster', async () => {
    const fixture = TestBed.createComponent(CpTopbarComponent);
    await fixture.whenStable();

    const link = fixture.nativeElement.querySelector('a[href="/patients"]');
    expect(link.textContent.trim()).toBe('Patients');
  });

  it('should toggle the theme and update the toggle button', async () => {
    const fixture = TestBed.createComponent(CpTopbarComponent);
    const theme = TestBed.inject(ThemeService);
    await fixture.whenStable();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '.cp-topbar__iconbtn',
    );
    expect(button.getAttribute('aria-label')).toBe('Switch to dark mode');
    expect(button.textContent?.trim()).toBe('dark_mode');

    button.click();
    await fixture.whenStable();

    expect(theme.isDark()).toBe(true);
    expect(button.getAttribute('aria-label')).toBe('Switch to light mode');
    expect(button.textContent?.trim()).toBe('light_mode');
  });

  it('should show a placeholder toast for unbuilt sections', async () => {
    const fixture = TestBed.createComponent(CpTopbarComponent);
    const show = vi.spyOn(TestBed.inject(ToastService), 'show');
    await fixture.whenStable();
    const items: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll(
        '.cp-topbar__nav-item:not([href])',
      ),
    );

    items.forEach((i) => i.click());

    expect(show.mock.calls.map((c) => c[0])).toEqual([
      'Schedule is a placeholder in this prototype',
      'Orders is a placeholder in this prototype',
      'Inbox is a placeholder in this prototype',
    ]);
  });

  it('should open unbuilt sections from the keyboard', async () => {
    const fixture = TestBed.createComponent(CpTopbarComponent);
    const show = vi.spyOn(TestBed.inject(ToastService), 'show');
    await fixture.whenStable();
    const [schedule, orders]: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll(
        '.cp-topbar__nav-item:not([href])',
      ),
    );
    const space = new KeyboardEvent('keydown', { key: ' ', cancelable: true });

    schedule.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    orders.dispatchEvent(space);

    expect(show.mock.calls.map((c) => c[0])).toEqual([
      'Schedule is a placeholder in this prototype',
      'Orders is a placeholder in this prototype',
    ]);
    expect(space.defaultPrevented).toBe(true);
    expect(schedule.getAttribute('tabindex')).toBe('0');
  });

  it('should show the signed-in user and sign out', async () => {
    const fixture = TestBed.createComponent(CpTopbarComponent);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    expect(
      el.querySelector('.cp-topbar__user-avatar')?.textContent?.trim(),
    ).toBe('AP');
    expect(el.querySelector('.cp-topbar__user-name')?.textContent).toBe(
      'Anika Patel',
    );
    expect(el.querySelector('.cp-topbar__user-role')?.textContent).toBe(
      'Clinician · Internal Medicine',
    );

    (el.querySelector('[aria-label="Sign out"]') as HTMLButtonElement).click();

    expect(auth.logout).toHaveBeenCalled();
  });

  it('should hide clinical sections from the front desk', async () => {
    auth.user.set({
      username: 'frontdesk',
      name: 'Jordan Lee',
      title: null,
      practitionerId: null,
      roles: ['FRONT_DESK'],
    });
    const fixture = TestBed.createComponent(CpTopbarComponent);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    const sections = Array.from(
      el.querySelectorAll('.cp-topbar__nav-item:not([href])'),
    ).map((item) => item.textContent?.trim());

    expect(sections).toEqual(['Schedule']);
    expect(el.querySelector('.cp-topbar__user-role')?.textContent).toBe(
      'Front desk',
    );
  });

  it('should hide the user block when signed out', async () => {
    auth.user.set(null);
    const fixture = TestBed.createComponent(CpTopbarComponent);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('.cp-topbar__user')).toBeNull();
  });
});
