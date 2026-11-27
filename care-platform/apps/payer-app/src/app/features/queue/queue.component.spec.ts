import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { QueueComponent } from './queue.component';

describe('QueueComponent', () => {
  const path = '/api/prior-auths?status=pended';
  const standard = {
    claimId: 'standard',
    authorizationReference: 'PHP-PA-0001',
    memberId: 'PHP-10006',
    memberName: 'Tony Stark',
    service: 'Cardiac stress test',
    expedited: false,
    decisionDeadline: '2026-11-26T15:00:00Z',
    secondsRemaining: -86400,
    breached: true,
  };
  const expedited = {
    ...standard,
    claimId: 'expedited',
    memberName: 'Sooner expedited',
    expedited: true,
    decisionDeadline: '2026-11-28T14:30:00+00:00',
    secondsRemaining: 5400,
    breached: false,
  };
  const later = {
    ...expedited,
    claimId: 'later',
    memberName: 'Later expedited',
    decisionDeadline: '2026-11-28T15:30:00Z',
    secondsRemaining: 0,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows loading and an empty queue', async () => {
    const fixture = TestBed.createComponent(QueueComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(
      el.querySelector('[aria-label="Loading review queue"]'),
    ).not.toBeNull();

    TestBed.inject(HttpTestingController).expectOne(path).flush({ items: [] });
    await fixture.whenStable();
    expect(el.querySelector('h1')?.textContent).toBe('Review queue');
    expect(el.querySelector('[role="status"]')?.textContent).toContain(
      'No prior authorization requests to review',
    );
  });

  it('puts expedited requests first, sorts by deadline within a priority, and shows the breach', async () => {
    const fixture = TestBed.createComponent(QueueComponent);
    TestBed.inject(HttpTestingController)
      .expectOne(path)
      .flush({
        items: [standard, later, expedited],
      });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const rows = Array.from(
      el.querySelectorAll('tbody tr'),
      (row) => row.textContent,
    );

    expect(rows).toHaveLength(3);
    expect(rows[0]).toContain('Sooner expedited');
    expect(rows[0]).toContain('1h 30m left');
    expect(rows[1]).toContain('Later expedited');
    expect(rows[1]).toContain('Due now');
    expect(rows[2]).toContain('Tony Stark');
    expect(rows[2]).toContain('Breached');
    expect(rows[2]).toContain('1d 0h overdue');
    expect(
      el.querySelector('tbody tr:last-child time')?.getAttribute('datetime'),
    ).toBe(standard.decisionDeadline);
  });

  it('can retry a failed queue request', async () => {
    const fixture = TestBed.createComponent(QueueComponent);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(path).flush({}, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'Could not load',
    );
    el.querySelector('button')?.click();
    http.expectOne(path).flush({
      items: [{ ...standard, secondsRemaining: 120, breached: false }],
    });
    await fixture.whenStable();
    expect(el.querySelector('tbody tr')?.textContent).toContain('2 min left');
    expect(
      fixture.componentInstance.remaining({
        ...standard,
        secondsRemaining: -1,
      }),
    ).toBe('Under 1 min overdue');
  });
});
