import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CpTopbarComponent } from './cp-topbar.component';
import { ThemeService } from '../../../../theme.service';
import { ToastService } from '../../../../toast.service';

describe('CpTopbarComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [CpTopbarComponent],
      providers: [provideRouter([])],
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
});
