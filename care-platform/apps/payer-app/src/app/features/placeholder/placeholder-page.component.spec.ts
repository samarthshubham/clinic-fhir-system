import { TestBed } from '@angular/core/testing';
import { PlaceholderPageComponent } from './placeholder-page.component';

describe('PlaceholderPageComponent', () => {
  it('should show the page heading, icon and what is coming', async () => {
    const fixture = TestBed.createComponent(PlaceholderPageComponent);
    fixture.componentRef.setInput('heading', 'Members');
    fixture.componentRef.setInput('icon', 'group');
    fixture.componentRef.setInput(
      'description',
      'Search members and see their coverage.',
    );
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('h1')?.textContent).toBe('Members');
    expect(el.querySelector('mat-icon')?.textContent?.trim()).toBe('group');
    expect(el.querySelector('[role="status"]')?.textContent).toContain(
      'Coming soon',
    );
    expect(el.textContent).toContain('Search members and see their coverage.');
  });
});
