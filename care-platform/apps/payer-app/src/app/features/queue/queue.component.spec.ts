import { TestBed } from '@angular/core/testing';
import { QueueComponent } from './queue.component';

describe('QueueComponent', () => {
  it('should show an empty queue', async () => {
    const fixture = TestBed.createComponent(QueueComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('h1')?.textContent).toBe('Review queue');
    expect(el.querySelector('[role="status"]')?.textContent).toContain(
      'No prior authorization requests to review',
    );
  });
});
