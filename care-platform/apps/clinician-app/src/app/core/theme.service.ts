import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly key = 'cp-theme';
  readonly isDark = signal(false);

  init(): void {
    const saved = localStorage.getItem(this.key);
    const prefersDark = window.matchMedia(
      '(prefers-color-scheme: dark)',
    ).matches;
    this.apply(saved === 'dark' || (!saved && prefersDark));
  }

  toggle(): void {
    this.apply(!this.isDark());
  }

  private apply(dark: boolean): void {
    document.documentElement.setAttribute(
      'data-theme',
      dark ? 'dark' : 'light',
    );
    localStorage.setItem(this.key, dark ? 'dark' : 'light');
    this.isDark.set(dark);
  }
}
