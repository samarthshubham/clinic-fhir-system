import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  let service: ThemeService;
  let prefersDark: boolean;

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    prefersDark = false;
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: prefersDark }) as MediaQueryList),
    );
    TestBed.configureTestingModule({});
    service = TestBed.inject(ThemeService);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('should use the saved dark theme', () => {
    localStorage.setItem('cp-theme', 'dark');

    service.init();

    expect(service.isDark()).toBe(true);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('should keep a saved light theme even when the system prefers dark', () => {
    localStorage.setItem('cp-theme', 'light');
    prefersDark = true;

    service.init();

    expect(service.isDark()).toBe(false);
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('should follow the system preference when nothing is saved', () => {
    prefersDark = true;

    service.init();

    expect(service.isDark()).toBe(true);
    expect(localStorage.getItem('cp-theme')).toBe('dark');
  });

  it('should default to light when nothing is saved and the system prefers light', () => {
    service.init();

    expect(service.isDark()).toBe(false);
    expect(localStorage.getItem('cp-theme')).toBe('light');
  });

  it('should toggle between light and dark and persist the choice', () => {
    service.init();

    service.toggle();
    expect(service.isDark()).toBe(true);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem('cp-theme')).toBe('dark');

    service.toggle();
    expect(service.isDark()).toBe(false);
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(localStorage.getItem('cp-theme')).toBe('light');
  });
});
