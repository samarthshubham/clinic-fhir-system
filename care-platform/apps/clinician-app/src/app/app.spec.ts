import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
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

describe('App', () => {
  async function render(auth: ReturnType<typeof fakeAuth>) {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('should show the top bar once signed in', async () => {
    const el = await render(fakeAuth('CLINICIAN'));

    expect(el.querySelector('cp-topbar')).not.toBeNull();
  });

  it('should hide the top bar while signed out', async () => {
    const el = await render(fakeAuth());

    expect(el.querySelector('cp-topbar')).toBeNull();
  });
});
