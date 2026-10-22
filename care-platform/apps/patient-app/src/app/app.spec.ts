import { TestBed } from '@angular/core/testing';
import { computed, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { AuthService, AuthUser } from '@care-platform/auth';
import { App } from './app';

const TONY: AuthUser = {
  username: 'tony',
  name: 'Tony Stark',
  title: null,
  practitionerId: null,
  patientId: 'seed-tony-stark',
  roles: ['PATIENT'],
};

describe('App', () => {
  let user: ReturnType<typeof signal<AuthUser | null>>;
  let logout: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    user = signal<AuthUser | null>(TONY);
    logout = vi.fn();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            user,
            isAuthenticated: computed(() => user() !== null),
            logout,
          },
        },
      ],
    }).compileComponents();
  });

  it('should show the signed-in patient and their initials', () => {
    const app = TestBed.createComponent(App).componentInstance;

    expect(app.patientName()).toBe('Tony Stark');
    expect(app.patientInitials()).toBe('TS');
    expect(app.showNav()).toBe(true);
  });

  it('should hide navigation and initials when signed out', () => {
    user.set(null);
    const app = TestBed.createComponent(App).componentInstance;

    expect(app.showNav()).toBe(false);
    expect(app.patientInitials()).toBe('?');
  });

  it('should sign out', () => {
    TestBed.createComponent(App).componentInstance.signOut();

    expect(logout).toHaveBeenCalled();
  });
});
