import { APP_INITIALIZER, FactoryProvider } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { appConfig } from './app.config';
import { ThemeService } from '@core/theme.service';

describe('appConfig', () => {
  const initializers = appConfig.providers.filter(
    (p): p is FactoryProvider =>
      typeof p === 'object' && 'provide' in p && p.provide === APP_INITIALIZER,
  );

  it('should initialise the theme on startup', () => {
    const theme = { init: vi.fn() };
    const factory = initializers.find((p) => p.deps?.[0] === ThemeService);

    factory?.useFactory(theme)();

    expect(theme.init).toHaveBeenCalled();
  });

  it('should use Material Symbols as the default icon font', () => {
    const registry = { setDefaultFontSetClass: vi.fn() };
    const factory = initializers.find((p) => p.deps?.[0] === MatIconRegistry);

    factory?.useFactory(registry)();

    expect(registry.setDefaultFontSetClass).toHaveBeenCalledWith(
      'material-symbols-outlined',
    );
  });
});
