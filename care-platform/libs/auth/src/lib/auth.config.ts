import { InjectionToken, Provider } from '@angular/core';

export interface AuthConfig {
  loginPath: string;
  homePath: string;
  refreshTokenKey: string;
}

export const AUTH_CONFIG = new InjectionToken<AuthConfig>('AUTH_CONFIG');

export function provideAuth(config: AuthConfig): Provider {
  return { provide: AUTH_CONFIG, useValue: config };
}
