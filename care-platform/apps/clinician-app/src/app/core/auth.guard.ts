import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from './auth.models';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).isAuthenticated()) return true;
  return inject(Router).createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url },
  });
};

export const guestGuard: CanActivateFn = () => {
  if (!inject(AuthService).isAuthenticated()) return true;
  return inject(Router).createUrlTree(['/patients']);
};

export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    if (inject(AuthService).hasRole(...roles)) return true;
    return inject(Router).createUrlTree(['/patients']);
  };
}
