import {
  ApplicationConfig,
  APP_INITIALIZER,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import {
  provideHttpClient,
  withFetch,
  withInterceptors,
} from '@angular/common/http';
import { MatIconRegistry } from '@angular/material/icon';
import { appRoutes } from './app.routes';
import { ThemeService } from '@core/theme.service';
import { AuthService } from '@core/auth.service';
import { authInterceptor } from '@core/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(appRoutes),
    provideAnimationsAsync(),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    {
      provide: APP_INITIALIZER,
      useFactory: (theme: ThemeService) => () => theme.init(),
      deps: [ThemeService],
      multi: true,
    },
    {
      provide: APP_INITIALIZER,
      useFactory: (auth: AuthService) => () => auth.restoreSession(),
      deps: [AuthService],
      multi: true,
    },
    {
      provide: APP_INITIALIZER,
      useFactory: (registry: MatIconRegistry) => () => {
        registry.setDefaultFontSetClass('material-symbols-outlined');
      },
      deps: [MatIconRegistry],
      multi: true,
    },
  ],
};
