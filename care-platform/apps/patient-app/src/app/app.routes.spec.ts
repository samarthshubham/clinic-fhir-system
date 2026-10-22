import { authGuard, guestGuard } from '@care-platform/auth';
import { appRoutes } from './app.routes';
import { LoginComponent } from './features/login/login.component';

describe('appRoutes', () => {
  function route(path: string) {
    const found = appRoutes.find((r) => r.path === path);
    if (!found) throw new Error(`missing route ${path}`);
    return found;
  }

  it('should open the login page for signed-out users only', async () => {
    expect(route('login').canActivate).toEqual([guestGuard]);
    expect(await route('login').loadComponent?.()).toBe(LoginComponent);
  });

  it('should require sign-in for every patient page', () => {
    for (const path of [
      'home',
      'vitals',
      'vitals/record',
      'messages',
      'profile',
    ]) {
      expect(route(path).canActivate).toEqual([authGuard]);
    }
  });

  it('should no longer offer the profile picker', () => {
    expect(appRoutes.some((r) => r.path === 'select')).toBe(false);
  });
});
